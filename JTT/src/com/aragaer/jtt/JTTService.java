package com.aragaer.jtt;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

public class JTTService extends Service {
    private static final String TAG = JTTService.class.getSimpleName();
    private JTT calculator;
    private NotificationManager nm;
    private static final int flags_ongoing = Notification.FLAG_ONGOING_EVENT
            | Notification.FLAG_NO_CLEAR;
    private static final int APP_ID = 0;
    private PendingIntent pending_main;
    private JTTHour.StringsHelper hs;
    private JTTTicker ticker = new JTTTicker();

    private boolean notify, force_stop = false;

    ArrayList<Messenger> mClients = new ArrayList<Messenger>();
    public static final int MSG_TOGGLE_NOTIFY = 0;
    public static final int MSG_UPDATE_LOCATION = 1;
    public static final int MSG_REGISTER_CLIENT = 2;
    public static final int MSG_UNREGISTER_CLIENT = 3;
    public static final int MSG_HOUR = 4;
    public static final int MSG_STOP = 5;
    public static final int MSG_TRANSITIONS = 6;
    public static final int MSG_INVALIDATE = 7;

    final Messenger mMessenger = new Messenger(new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_TOGGLE_NOTIFY:
                notify = msg.getData().getBoolean("notify");
                if (notify)
                    notify_helper(ticker.hn, ticker.hf);
                else
                    nm.cancel(APP_ID);
                break;
            case MSG_UPDATE_LOCATION:
                String ll[] = msg.getData().getString("latlon").split(":");
                calculator.move(Float.parseFloat(ll[0]),
                        Float.parseFloat(ll[1]));
                ticker.reset();
                informClients(Message.obtain(null, MSG_INVALIDATE));
                break;
            case MSG_REGISTER_CLIENT:
                try {
                    msg.replyTo.send(Message.obtain(null, MSG_HOUR, ticker.hn,
                            ticker.hf));
                    mClients.add(msg.replyTo);
                } catch (RemoteException e) {
                    Log.w(TAG, "Client registered but failed to get data");
                }
                break;
            case MSG_UNREGISTER_CLIENT:
                mClients.remove(msg.replyTo);
                break;
            case MSG_STOP:
                force_stop = true;
                stopSelf();
                break;
            case MSG_TRANSITIONS:
                try {
                    msg.replyTo.send(d_trans_msg(msg.arg1));
                } catch (RemoteException e) {
                    Log.w(TAG, "Client requested transitions data but failed to get answer");
                }
                break;
            default:
                super.handleMessage(msg);
                break;
            }
        }
    });

    private Message d_trans_msg(int jd) {
        Bundle b = new Bundle();
        Message res = Message.obtain(null, MSG_TRANSITIONS);
        b.putLongArray("tr", calculator.computeTr(jd));
        res.setData(b);
        return res;
    }

    private String app_name;
    private static final DateFormat df = new SimpleDateFormat("HH:mm");
    private void notify_helper(int hn, int hf) {
        Notification n = new Notification(R.drawable.notification_icon,
                app_name, System.currentTimeMillis());
        RemoteViews rv = new RemoteViews(getPackageName(),
                R.layout.notification);

        n.flags = flags_ongoing;
        n.iconLevel = hn;
        rv.setTextViewText(R.id.image, JTTHour.Glyphs[hn]);
        rv.setTextViewText(R.id.title, hs.getHrOf(hn));
        rv.setTextViewText(R.id.percent, String.format("%d%%", hf));
        rv.setProgressBar(R.id.fraction, 100, hf, false);
        rv.setTextViewText(R.id.start, df.format(ticker.t_start));
        rv.setTextViewText(R.id.end, df.format(ticker.t_end));

        n.contentIntent = pending_main;
        n.contentView = rv;
        nm.notify(APP_ID, n);
    }

    private void doNotify(int n, int f) {
        if (notify)
            notify_helper(n, f);
        informClients(Message.obtain(null, MSG_HOUR, n, f));
    }

    private void informClients(Message msg) {
        int i = mClients.size();
        if (i == 0)
            return;
        while (i-- > 0)
            try {
                mClients.get(i).send(msg);
            } catch (RemoteException e) {
                /*
                 * The client is dead. Remove it from the list; we are going
                 * through the list from back to front so this is safe to do
                 * inside the loop.
                 */
                mClients.remove(i);
            }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    private final BroadcastReceiver on = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ticker.start_ticking();
        }
    };
    private final BroadcastReceiver off = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ticker.stop_ticking();
        }
    };

    @Override
    public void onStart(Intent intent, int startid) {
        Log.i(TAG, "Service starting");
        hs = new JTTHour.StringsHelper(this);
        SharedPreferences settings = PreferenceManager
                .getDefaultSharedPreferences(getBaseContext());
        String[] ll = settings.getString("jtt_loc", "0.0:0.0").split(":");

        calculator = new JTT(Float.parseFloat(ll[0]), Float.parseFloat(ll[1]));

        Intent JTTMain = new Intent(getBaseContext(), JTTMainActivity.class);
        pending_main = PendingIntent.getActivity(this, 0, JTTMain, 0);
        notify = settings.getBoolean("jtt_notify", true);
        app_name = getString(R.string.app_name);
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (!notify)
            nm.cancel(APP_ID);

        registerReceiver(on, new IntentFilter(Intent.ACTION_SCREEN_ON));
        registerReceiver(off, new IntentFilter(Intent.ACTION_SCREEN_OFF));

        ticker.start_ticking();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Service destroying");

        unregisterReceiver(on);
        unregisterReceiver(off);

        ticker.stop_ticking();

        if (force_stop)
            nm.cancel(APP_ID);
        else {
            SharedPreferences settings = PreferenceManager
                    .getDefaultSharedPreferences(getBaseContext());
            final boolean boot = settings.getBoolean("jtt_bootup", true);
            if (notify || boot) {
                Notification n = new Notification(R.drawable.notification_icon,
                        app_name, System.currentTimeMillis());

                n.setLatestEventInfo(JTTService.this, getString(R.string.srv_fail),
                        getString(R.string.srv_fail_ex), pending_main);
                n.flags = boot ? flags_ongoing : 0;
                nm.notify(APP_ID, n);
            }
        }
    }

    public static class JTTStartupReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            if (PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean("jtt_bootup", true))
                context.startService(new Intent(context, JTTService.class));
        }
    }

    private final class JTTTicker extends Ticker {
        protected int start_day, end_day;
        public int hn, hf;

        public JTTTicker() {
            super(6, 100);
            start_day = end_day = JTT.longToJDN(System.currentTimeMillis());
        }

        @Override
        public int overrun() {
            long[] t = calculator.computeTr(start_day++);
            for (long l : t)
                add_tr(l);
            return KEEP_TICKING;
        }
        @Override
        public void reset() {
            start_day = end_day = JTT.longToJDN(System.currentTimeMillis());
            super.reset();
        }
        @Override
        public void handle_sub(int tick, int sub) {
            doNotify(hn, hf = sub);
        }
        @Override
        public void handle_tick(int tick, int sub) {
            int pos = Collections.binarySearch(tr, System.currentTimeMillis());
            /* possible results:
             * pos >= 0 - equal to one of transitions
             * pos < 0 - goes between transitions, -pos - 2 is the previous one
             */
            if (pos < 0)
                pos = -2 - pos;
            /* every even position is a sunrise */
            int isDay = (pos + 1) % 2;
            doNotify(hn = (tick + isDay * 6) % 12, hf = sub);
        }

        @Override
        protected int underrun() {
            // time went backwards..
            long[] t = calculator.computeTr(end_day--);
            for (long l : t)
                add_tr(l);
            return KEEP_TICKING;
        }
    };
}
