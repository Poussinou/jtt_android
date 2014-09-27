package com.aragaer.jtt.today;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.aragaer.jtt.R;
import com.aragaer.jtt.core.Hour;
import com.aragaer.jtt.resources.RuntimeResources;
import com.aragaer.jtt.resources.StringResources;

/* Hour item in TodayList */
class HourItem extends TodayItem {
	public final int hnum;

	public HourItem(long t, int h) {
		super(t);
		hnum = h % 12;
	}

	static String[] extras;

	@Override
	public View toView(Context c, View v, int sel_p_diff) {
		if (v == null)
			v = View.inflate(c, R.layout.today_item, null);
		final StringResources sr = RuntimeResources.get(c).getInstance(
				StringResources.class);

		((TextView) v.findViewById(R.id.glyph)).setText(Hour.Glyphs[hnum]);
		((TextView) v.findViewById(R.id.name)).setText(sr.getHrOf(hnum));
		((TextView) v.findViewById(R.id.extra)).setText(extras[hnum]);
		((TextView) v.findViewById(R.id.curr)).setText(sel_p_diff == 0 ? "▶"
				: "");

		return v;
	}
}