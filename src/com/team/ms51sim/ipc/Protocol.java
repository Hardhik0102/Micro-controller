package com.team.ms51sim.ipc;

/**
 * Message-type constants shared by all three processes.
 *
 * <pre>
 *   UI   -> Core   : REQ_*   (Core answers with RSP_STATE / RSP_ACK / RSP_ERROR)
 *   Core -> Logger : LOG_EVENT (one-way, no reply)   LOG_STATS_REQ -> LOG_STATS_RSP
 * </pre>
 * The full byte layout is documented in docs/week-04/ARCHITECTURE-AND-IPC.md.
 */
public final class Protocol {
    private Protocol() {}

    /* ---- UI -> Core ---- */
    public static final int REQ_LOAD        = 1;   // int n, n x uint8 machine code
    public static final int REQ_RESET       = 2;
    public static final int REQ_STEP        = 3;
    public static final int REQ_RUN         = 4;   // int maxSteps, boolean includeTrace
    public static final int REQ_STATE       = 5;
    public static final int REQ_SET_LOGGING = 6;   // boolean per-step logging on/off
    public static final int REQ_LOG         = 7;   // byte level, UTF message (UI -> Core -> Logger)
    public static final int REQ_PING        = 8;
    public static final int REQ_SHUTDOWN    = 9;

    /* ---- Core -> UI ---- */
    public static final int RSP_STATE = 101;
    public static final int RSP_ERROR = 102;       // UTF message
    public static final int RSP_ACK   = 103;

    /* ---- anything -> Logger ---- */
    public static final int LOG_EVENT     = 201;
    public static final int LOG_STATS_REQ = 202;
    public static final int LOG_STATS_RSP = 203;
    public static final int LOG_SHUTDOWN  = 204;   // answered with RSP_ACK
}
