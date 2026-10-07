package com.team.ms51sim.logging;

/** Counters reported by the Logger process (after it has flushed everything received so far). */
public record LogStats(long total, long debug, long info, long warn, long error) {
}
