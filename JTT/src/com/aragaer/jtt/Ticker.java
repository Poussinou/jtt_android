package com.aragaer.jtt;

import java.util.Date;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

public abstract class Ticker {
    public Date start, end;
    protected LinkedList<Long> tr = new LinkedList<Long>();

    private final static int MSG = 1;
    static final long ms_per_day = TimeUnit.SECONDS.toMillis(60 * 60 * 24);
    private long rate; // number of millis per 1% of hour
    private int ticks, subs, tick, sub;
    private double total;

    public Ticker(int ticks, int subs) {
        this.ticks = ticks;
        this.subs = subs;
        this.total = ticks * subs;
    }

    public void reset() {
        tr.clear();
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            synchronized (Ticker.this) {
                long lastTickStart = SystemClock.elapsedRealtime();
                if (++sub >= subs) {
                    sub %= subs;
                    if (++tick >= ticks)
                        lastTickStart += resync_tick();
                    handleTick(tick, sub);
                    start = end;
                    end = new Date(start.getTime() + subs * rate);
                } else
                    handleSub(tick, sub);

                // take into account user's onTick taking time to execute
                long delay = lastTickStart - SystemClock.elapsedRealtime() + rate;
                // special case: user's onTick took more than interval to
                // complete, skip to next interval
                while (delay < 0) {
                    delay += rate;
                    sub++;
                }
                sendMessageDelayed(obtainMessage(MSG), delay);
            }
        }
    };

    public final void stop_ticking() {
        mHandler.removeMessages(MSG);
    }

    public synchronized final void start_ticking() {
        long delay = resync_tick();
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG), delay);
        handleTick(tick, sub);
    }

    private void update_to(long ms) {
        int s = tr.size();
        do {
            while (s > 1) {
                if (ms < tr.get(1))
                    return;
                tr.remove();
                s--;
            }
            exhausted();
        } while ((s = tr.size()) > 1);
    }

    /* updates rate, recalculates now, returns delay to next tick */
    private long resync_tick() {
        long ms = System.currentTimeMillis();
        update_to(ms);
        final long tr0 = tr.get(0);
        final long tr1 = tr.get(1);
        rate = Math.round((tr1 - tr0) / total);
        double h = total * (ms - tr0) / (tr1 - tr0);
        tick = (int) h / subs;
        sub = (int) h % subs;
        final long start_ms = ms - sub * rate;
        start = new Date(start_ms);
        end = new Date(start_ms + rate * subs);
        return rate - (ms - tr0) % rate;
    }

    public abstract void exhausted();

    public abstract void handleTick(int tick, int sub);

    public abstract void handleSub(int tick, int sub);
}
