// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt.core;

import java.util.*;
import java.util.concurrent.TimeUnit;

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;
import com.luckycatlabs.sunrisesunset.dto.Location;


public  class SscAdapter implements IntervalCalculator {
    private static SscAdapter instance;

    public static SscAdapter getInstance() {
        if (instance == null)
            instance = new SscAdapter();
        return instance;
    }

    private static final long ms_per_day = TimeUnit.SECONDS.toMillis(60 * 60 * 24);
    private final Map<Long, Interval> _cache = new HashMap<Long, Interval>();
    private SunriseSunsetCalculator _calculator;

    private SscAdapter() {
        setLocation(0, 0);
    }

    public void setLocation(float latitude, float longitude) {
        _calculator = new SunriseSunsetCalculator(new Location(latitude, longitude),
                                                  TimeZone.getDefault());
        _cache.clear();
    }

    @Override public Interval getDayIntervalForJDN(long jdn) {
        Interval result = _cache.get(jdn);
        if (result == null) {
            final Calendar date = Calendar.getInstance();
            date.setTimeInMillis(JDToLong(jdn));
            result = new Interval(_calculator.getOfficialSunriseCalendarForDate(date).getTimeInMillis(),
                                  _calculator.getOfficialSunsetCalendarForDate(date).getTimeInMillis(),
                                  true);
            _cache.put(jdn, result);
        }
        return result;
    }

    private static long JDToLong(final double jd) {
        return Math.round((jd - 2440587.5) * ms_per_day);
    }
}
