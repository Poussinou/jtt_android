package com.aragaer.jtt.core;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class Clockwork extends IntentService {
    public static final String ACTION_JTT_TICK = "com.aragaer.jtt.action.TICK";
    private static final Intent TickAction = new Intent(ACTION_JTT_TICK);
    private static final int INTENT_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT;
    private final Hour hour = new Hour(0);

    public Clockwork() {
	super("CLOCKWORK");
    }

    public static class TimeChangeReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
	    final String action = intent.getAction();
	    if (action.equals(Intent.ACTION_TIME_CHANGED)
		|| action.equals(Intent.ACTION_DATE_CHANGED))
		try {
		    schedule(context);
		} catch (IllegalStateException e) {
		    Log.i("JTT CLOCKWORK", "Time change while service is not running, ignore");
		}
	}
    };

    private final static double total = Hour.HOURS * Hour.HOUR_PARTS;

    public static void schedule(final Context context) {
	final AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
	final long tr[] = new long[4];
	final boolean is_day = Calculator.getSurroundingTransitions(context, System.currentTimeMillis(), tr);
	ThreeIntervals intervals = new ThreeIntervals(tr);

	final long freq = Math.round((tr[2] - tr[1])/total);

	final Intent TickActionInternal = new Intent(context, Clockwork.class)
	    .putExtra("intervals", intervals)
	    .putExtra("day", is_day);

	/* Tell alarm manager to start ticking at tr[1], it will automatically calculate the next tick time */
	am.setRepeating(AlarmManager.RTC, tr[1], freq, PendingIntent.getService(context, 0, TickActionInternal, INTENT_FLAGS));
    }

    public static void unschedule(final Context context) {
	final AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
	am.cancel(PendingIntent.getService(context, 0, new Intent(context, Clockwork.class), 0));
    }

    @Override
    protected void onHandleIntent(Intent intent) {
	final ThreeIntervals intervals = (ThreeIntervals) intent.getSerializableExtra("intervals");
	final long tr[] = intervals.getTransitions();
	final boolean is_day = intent.getBooleanExtra("day", false);
	final long now = System.currentTimeMillis();
	Hour.fromTimestamps(tr, is_day, now, hour);

	if (intervals.surrounds(now)) {
	    TickAction.putExtra("intervals", intervals)
		.putExtra("day", is_day)
		.putExtra("hour", hour.num)
		.putExtra("jtt", hour.wrapped);
	    sendStickyBroadcast(TickAction);
	} else
	    try {
		schedule(this);
	    } catch (IllegalStateException e) {
		Log.i("JTT CLOCKWORK", "Transition passed while service is not running, ignore");
	    }

	stopSelf();
    }
}
