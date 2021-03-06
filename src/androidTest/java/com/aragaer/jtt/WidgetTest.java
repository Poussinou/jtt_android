// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt;

import android.content.*;
import android.graphics.Point;
import android.preference.PreferenceManager;
import android.support.test.espresso.*;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.*;
import android.view.*;
import android.widget.*;

import org.junit.*;
import org.junit.runner.RunWith;

import static android.support.test.InstrumentationRegistry.*;
import static org.junit.Assert.*;


@LargeTest
@RunWith(AndroidJUnit4.class)
public class WidgetTest {

    private static final int LAUNCH_TIMEOUT = 5000;

    public void setInitialLocation(Context context) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putString(Settings.PREF_LOCATION, "0.0:0.0");
        editor.putString(Settings.PREF_LOCALE, "1");
        editor.commit();
    }

    @Before public void setUp() {
        Context context = getInstrumentation().getTargetContext();
        setInitialLocation(context);
        context.startService(new Intent(getTargetContext(), JttService.class));
    }

    private void putWidgetOnHome(String widgetName) throws Exception {
        UiDevice device = UiDevice.getInstance(getInstrumentation());
        device.pressHome();
        String launcherPackageName = device.getLauncherPackageName();
        if (!launcherPackageName.equals("com.android.launcher"))
            return;
        device.wait(Until.hasObject(By.pkg(launcherPackageName).depth(0)), LAUNCH_TIMEOUT);
        device.findObject(By.desc("Apps")).click();

        BySelector widgets = By.text("Widgets").desc("Widgets");
        device.wait(Until.hasObject(widgets), LAUNCH_TIMEOUT);
        device.findObject(widgets).click();
        device.wait(Until.hasObject(widgets.selected(true)), LAUNCH_TIMEOUT);

        UiScrollable scrollable = new UiScrollable(new UiSelector().scrollable(true));
        scrollable.setAsHorizontalList();
        scrollable.flingToEnd(10);
        UiObject widget = scrollable.getChildByText(new UiSelector().className(TextView.class), widgetName);

        widget.dragTo(device.getDisplayWidth()/2, device.getDisplayHeight()/2, 40);
        device.pressHome();
        device.wait(Until.hasObject(By.pkg(launcherPackageName).depth(0)), LAUNCH_TIMEOUT);
        device.wait(Until.hasObject(By.desc(widgetName)), LAUNCH_TIMEOUT);
    }

    @Test public void testClockWidget() throws Exception {
        putWidgetOnHome("JTT: Clock");
    }

    @Test public void testHourWidget() throws Exception {
        putWidgetOnHome("JTT: Single hour");
    }
}
