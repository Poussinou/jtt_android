package com.aragaer.jtt;
// vim: et ts=4 sts=4 sw=4

public class SscAdapter implements DayIntervalCalculator {
    public void setLocation(Location location) {
    }
    public DayInterval getIntervalFor(long timestamp) {
        return new Day(timestamp-1, timestamp+1);
    }
}
