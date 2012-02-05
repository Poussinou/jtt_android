package com.aragaer.jtt;

import java.util.Date;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.RemoteViews;

public class JTTService extends Service {
    private JTT calculator;
    private JTTHour hour = new JTTHour(0);
    private Notification notification;
    private NotificationManager nm;
    private static final int APP_ID = 0;
    private Intent JTTMain;
    private PendingIntent pending_main;
    private SharedPreferences settings;

    private static final String TAG = JTTService.class.getSimpleName();

    private IJTTService.Stub apiEndpoint = new IJTTService.Stub() {
        public JTTHour getHour() {
            synchronized (hour) {
                return hour;
            }
        }
    };

    private Timer timer;
    private TimerTask updateTask = new TimerTask() {
        @Override
        public void run() {
            Date when = new Date();
            final Context ctx = getBaseContext();
            hour = calculator.time_to_jtt(when);
            if (settings.getBoolean("jtt_notify", true)) {
                if (notification.contentView == null) {
                    RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.notification);
                    notification.contentView = contentView;
                }
                if (notification.contentIntent == null)
                    notification.contentIntent = pending_main;

                notification.contentView.setTextViewText(R.id.image, JTTHour.Glyphs[hour.num]);
                notification.contentView.setTextViewText(R.id.title, ctx.getString(R.string.hr_of)+" "+hour.hour);
                notification.contentView.setTextViewText(R.id.text, Math.round(hour.fraction * 100)+"%");
                notification.contentView.setTextViewText(R.id.when, DateFormat.format("hh:mm", when));
                
                notification.iconLevel = hour.num;
                nm.notify(APP_ID, notification);
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        if (JTTService.class.getName().equals(intent.getAction())) {
            Log.d(TAG, "Bound by intent " + intent);
            return apiEndpoint;
        } else {
            return null;
        }
    }

    @Override
    public void onStart(Intent intent, int startid) {
        float latitude, longitude;
        Log.i(TAG, "Service starting");
        settings = PreferenceManager
                .getDefaultSharedPreferences(getBaseContext());
        String[] ll = settings.getString("jtt_loc", "0.0:0.0").split(":");
        latitude = Float.parseFloat(ll[0]);
        longitude = Float.parseFloat(ll[1]);

        calculator = new JTT(latitude, longitude, TimeZone.getDefault());
        hour = calculator.time_to_jtt(new Date());
        Log.d(TAG, "rate = "+calculator.rate);
        Log.d(TAG, "Next hour at "+calculator.nextHour.toLocaleString());

        JTTMain = new Intent(getBaseContext(), JTTMainActivity.class);
        pending_main = PendingIntent.getActivity(getBaseContext(), 0, JTTMain, 0);

        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notification = new Notification(R.drawable.notification_icon,
                getBaseContext().getString(R.string.app_name), System.currentTimeMillis());
        notification.flags = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
        
        timer = new Timer("JTTServiceTimer");
        try {
            timer.scheduleAtFixedRate(updateTask, 0, 60 * 1000L);
        } catch (IllegalStateException e) {
            Log.i(TAG, "Timer is already running");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Service destroying");

        timer.cancel();
        timer = null;

        notification.setLatestEventInfo(JTTService.this,
                getBaseContext().getString(R.string.srv_fail),
                getBaseContext().getString(R.string.srv_fail_ex),
                pending_main);
        notification.when = System.currentTimeMillis();
        notification.iconLevel = hour.num;
        nm.notify(APP_ID, notification);
    }
}
