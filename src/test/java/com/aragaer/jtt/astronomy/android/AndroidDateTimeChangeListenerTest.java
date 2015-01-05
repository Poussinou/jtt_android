package com.aragaer.jtt.astronomy.android;
// vim: et ts=4 sts=4 sw=4

import java.util.List;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.*;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import android.content.*;

import com.aragaer.jtt.astronomy.DayIntervalService;
import com.aragaer.jtt.location.Location;


@RunWith(RobolectricTestRunner.class)
@Config(emulateSdk = 18)
public class AndroidDateTimeChangeListenerTest {

    private AndroidDateTimeChangeListener listener;
    private TestDayIntervalService astrolabe;

    @Before public void setup() {
        listener = new AndroidDateTimeChangeListener();
        astrolabe = new TestDayIntervalService();
        listener.setService(astrolabe);
        listener.register(Robolectric.application);
    }

    private void testListensFor(String action) {
        ShadowApplication shadowApplication = Robolectric.getShadowApplication();
        Intent intent = new Intent(action);
        List<BroadcastReceiver> receiversForIntent = shadowApplication.getReceiversForIntent(intent);
        assertThat(receiversForIntent.size(), equalTo(1));
    }

    @Test public void shouldListenForTimeChange() {
        testListensFor(Intent.ACTION_TIME_CHANGED);
    }

    @Test public void shouldListenForDateChange() {
        testListensFor(Intent.ACTION_DATE_CHANGED);
    }

    @Test public void shouldListenForTimeTick() {
        testListensFor(Intent.ACTION_TIME_TICK);
    }

    @Test public void shouldReportCurrentTime() {
        long begin = System.currentTimeMillis();
        listener.onReceive(Robolectric.application, new Intent(Intent.ACTION_DATE_CHANGED));
        long end = System.currentTimeMillis();

        assertThat(astrolabe.currentTime, greaterThanOrEqualTo(begin));
        assertThat(astrolabe.currentTime, lessThanOrEqualTo(end));
    }

    private static class TestDayIntervalService extends DayIntervalService {
        public long currentTime;

        public TestDayIntervalService() {
            super(null);
        }

        @Override public void timeChanged(long timestamp) {
            currentTime = timestamp;
        }
    }
}