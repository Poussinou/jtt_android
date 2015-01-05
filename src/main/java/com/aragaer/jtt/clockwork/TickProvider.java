package com.aragaer.jtt.clockwork;
// vim: et ts=4 sts=4 sw=4

public interface TickProvider {
    public void attachTo(TickCounter cogs);
    public void start(long start, long tickLength);
    public void stop();
}