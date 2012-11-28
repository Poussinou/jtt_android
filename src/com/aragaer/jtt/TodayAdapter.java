package com.aragaer.jtt;

import java.util.Collections;
import java.util.LinkedList;
import java.util.TimeZone;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

/* A single item in TodayList */
abstract class TodayItem {
	public final long time;
	abstract public View toView(Context c);
	public TodayItem(long t) {
		time = t;
	}
}

/* Hour item in TodayList */
class HourItem extends TodayItem {
	public static int current;
	public static long next_transition;
	public final int hnum;
	public final String date;

	public HourItem(long t, int h) {
		super(t);
		hnum = h % 12;
		date = JTTUtil.format_time(t);
	}

	static String[] extras = null, hours = null;

	public View toView(Context c) {
		if (extras == null) {
			JTTUtil.initLocale(c);
			extras = new String[] { c.getString(R.string.sunset), "", "",
					c.getString(R.string.midnight), "", "",
					c.getString(R.string.sunrise), "", "",
					c.getString(R.string.midday), "", "" };
			hours = c.getResources().getStringArray(R.array.hour_of);

		}
		View v = View.inflate(c, R.layout.today_item, null);

		/* no need to check for previous transition */
		boolean is_current = hnum == current
				&& time < next_transition;

		JTTUtil.t(v, R.id.time, date);
		JTTUtil.t(v, R.id.glyph, JTTHour.Glyphs[hnum]);
		JTTUtil.t(v, R.id.name, hours[hnum]);
		JTTUtil.t(v, R.id.extra, extras[hnum]);
		JTTUtil.t(v, R.id.curr, is_current ? "▶" : "");

		return v;
	}
}

/* "DayName" item in Today List */
class DayItem extends TodayItem {
	public DayItem(long t) {
		super(t);
	}

	public View toView(Context c) {
		View v = View.inflate(c, android.R.layout.preference_category, null);
		JTTUtil.t(v, android.R.id.title, dateToString(time, c));

		return v;
	}

	/* returns strings like "today" or "2 days ago" etc */
	static String[] daynames = null;
	private String dateToString(long date, Context c) {
		JTTUtil.initLocale(c);
		if (daynames == null)
			daynames = new String[] { c.getString(R.string.day_next),
				c.getString(R.string.day_curr),
				c.getString(R.string.day_prev) };

		final long now = System.currentTimeMillis();
		final int ddiff = (int) (ms_to_day(now) - ms_to_day(date));
		if (ddiff < 2 && ddiff > -2)
			return daynames[ddiff+1];
		return c.getResources().getQuantityString(ddiff > 0
				? R.plurals.days_past
				: R.plurals.days_future, ddiff, ddiff);
	}

	/* helper function
	 * converts ms timestamp to day number
	 * not useful by itself but can be used to find difference between days
	 */
	private static final long ms_to_day(long t) {
		t += TodayAdapter.tz.getOffset(t);
		return t / JTT.ms_per_day;
	}
}

public class TodayAdapter extends ArrayAdapter<TodayItem> {
	private static final String TAG = TodayAdapter.class.getSimpleName();
	private LinkedList<Long> transitions = new LinkedList<Long>();
	private long prev_transition, next_transition;
	static TimeZone tz = TimeZone.getDefault();

	/* true if we have requested transitions data and not got result yet */
	private boolean expecting_data = false;

	long jdn_min, jdn_max;

	public TodayAdapter(Context c, int layout_id) {
		super(c, layout_id);
		JTTUtil.initLocale(c);
		HourItem.extras = HourItem.hours = DayItem.daynames = null;
	}

	@Override
	public View getView(int position, View v, ViewGroup parent) {
		return getItem(position).toView(parent.getContext());
	}

	/* helper function
	 * accepts a time stamp
	 * returns a time stamp for the same time but next day
	 *
	 * simply adding 24 hours does not always work
	 */
	private static final long add24h(long t) {
		t += tz.getOffset(t);
		t += JTT.ms_per_day;
		return t - tz.getOffset(t);
	}

	/* takes a sublist of hours
	 * creates a list to display by adding day names
	 */
	void buildItems(long[] transitions, boolean is_day) {
		clear();

		/* time stamp for 00:00:00 */
		long start_of_day = transitions[0];

		/* "aligning" code */
		start_of_day += tz.getOffset(start_of_day);
		start_of_day -= start_of_day % JTT.ms_per_day;
		start_of_day -= tz.getOffset(start_of_day);

		add(new DayItem(start_of_day)); // List should start with one
		start_of_day = add24h(start_of_day);

		int h_add = is_day ? 6 : 0;

		/* start with first transition */
		add(new HourItem(transitions[0], h_add));
		for (int i = 1; i < transitions.length; i++) {
			long start = transitions[i - 1];
			long diff = transitions[i] - start;
			for (int j = 1; j <= 6; j++) {
				long t = start + j * diff / 6;
				if (t >= start_of_day) {
					add(new DayItem(start_of_day));
					start_of_day = add24h(start_of_day);
				}
				add(new HourItem(t, h_add + j));
			}
			h_add = 6 - h_add;
		}
	}

	/* handle bundle containing another couple of transitions
	 * if list is empty at the moment, we have only one day
	 * that starts with sunrise and ends with sunset
	 * if list is not empty, we have a new night and a day
	 * these two go to the beginning or to the end of the list
	 */
	public void addTransitions(Bundle b) {
		long jdn = b.getLong("jdn");
		long sunrise = b.getLong("sunrise");
		long sunset = b.getLong("sunset");
		expecting_data = false;

		if (transitions.isEmpty()) {
			transitions.add(sunrise);
			transitions.add(sunset);
			jdn_max = jdn_min = jdn;
		} else {
			if (jdn_max < jdn) {
				transitions.add(sunrise);
				transitions.add(sunset);
				jdn_max = jdn;
			} else if (jdn_min > jdn) { // add to front
				transitions.addFirst(sunset);
				transitions.addFirst(sunrise);
				jdn_min = jdn;
			} else {
				Log.e(TAG, "Got "+jdn+" which is between "+jdn_min+" and "+jdn_max);
				return;
			}
		}

		updateItems();
	}

	public void reset() {
		transitions.clear();
		getDay(JTT.longToJDN(System.currentTimeMillis()));
	}

	/* request transitions for given day from JTTService */
	private void getDay(long jdn) {
		if (expecting_data) {
			Log.e(TAG, "Transitions data requested while previous request is not yet handled");
			return;
		}

		Bundle b = new Bundle();
		b.putLong("jdn", jdn);
		expecting_data = true;
		((JTTMainActivity) getContext()).conn.send_msg_to_service(JTTService.MSG_TRANSITIONS, b);
	}

	private void getPastDay() {
		getDay(jdn_min - 1);
	}

	private void getFutureDay() {
		getDay(jdn_max + 1);
	}

	private void updateItems() {
		if (transitions.isEmpty() || (transitions.size() % 2) == 1) {
			Log.i(TAG, "Transitions list is empty or has incorrect number of items");
			reset();
			return;
		}

		int pos = Collections.binarySearch(transitions, System.currentTimeMillis());
		/* current time falls between pos and pos+1 */
		if (pos < 0)
			pos = -pos - 2;

		/* check if we have enough data for past */
		if (pos < 1) {
			getPastDay();
			return;
		}

		/* remove outdated stuff */
		while (pos > 2) {
			transitions.remove();
			transitions.remove();
			pos -= 2;
			jdn_min++;
		}

		/* now pos is exactly 1 or 2
		 * 1 means it is night now
		 * 2 means it is day now
		 */

		/* check if we have enough data for future */
		if (transitions.size() <= pos + 2) {
			getFutureDay();
			return;
		}

		/* it is possible that we have too much "future" information
		 * keep it
		 */

		/* exactly 4 transitions are used:
		 * pos-1, pos, pos+1 and pos+2
		 */
		long[] l = new long[4];
		for (int i = 0; i < 4; i++)
			l[i] = transitions.get(pos - 1 + i);

		prev_transition = l[1];
		HourItem.next_transition = next_transition = l[2];

		/* if it is day now then first interval is night */
		buildItems(l, pos == 1);
	}

	public void setCurrent(int cur) {
		long now = System.currentTimeMillis();
		HourItem.current = cur;

		/* Proper code to update timezone when it changed is quite complex
		 * The event itself is pretty rare
		 * Even if it breaks, restarting the app will fix that
		 */
		tz = TimeZone.getDefault();

		if (now >= prev_transition && now < next_transition)
			notifyDataSetChanged();
		else if (!expecting_data)
			updateItems();
	}


	@Override
	public boolean isEnabled(int pos) {
		return false;
	}
}