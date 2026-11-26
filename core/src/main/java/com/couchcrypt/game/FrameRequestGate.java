package com.couchcrypt.game;

/** One current render and one replaceable VSYNC credit shared by UI and GL threads. */
public final class FrameRequestGate {
    public static final int EMPTY = 0, BUSY = 1, EXPIRED = 2, CLAIMED = 3;
    private long pendingTimeNs, ticketSequence, currentTicket, admittedVsyncNs;

    public synchronized void reset() {
        pendingTimeNs = 0; currentTicket = 0; admittedVsyncNs = 0; ticketSequence++;
    }
    public synchronized void offer(long frameTimeNs) {
        if (frameTimeNs > pendingTimeNs) pendingTimeNs = frameTimeNs;
    }
    public synchronized int claim(long nowNs, long maximumAgeNs) {
        if (pendingTimeNs == 0) return EMPTY;
        if (nowNs - pendingTimeNs > maximumAgeNs) { pendingTimeNs = 0; return EXPIRED; }
        if (currentTicket != 0) return BUSY;
        currentTicket = ++ticketSequence; admittedVsyncNs = pendingTimeNs; pendingTimeNs = 0; return CLAIMED;
    }
    /** Also accounts for platform bootstrap/resize renders that were not explicitly requested. */
    public synchronized long beginDraw() {
        if (currentTicket == 0) { currentTicket = ++ticketSequence; admittedVsyncNs = 0; }
        return currentTicket;
    }
    /** Zero means bootstrap/unowned, completed, or invalidated by a lifecycle reset. */
    public synchronized long presentationTimeNs(long ticket, long framePeriodNs) {
        if (ticket != currentTicket || admittedVsyncNs <= 0 || framePeriodNs <= 0
            || admittedVsyncNs > Long.MAX_VALUE - framePeriodNs) return 0;
        return admittedVsyncNs + framePeriodNs;
    }
    public synchronized boolean complete(long ticket) {
        if (ticket != currentTicket) return false;
        currentTicket = 0; admittedVsyncNs = 0; return true;
    }
    public synchronized boolean inFlight() { return currentTicket != 0; }
}
