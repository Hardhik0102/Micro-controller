package com.team.ms51sim.ipc;

import com.team.ms51sim.Simulator;

import java.util.List;

/**
 * Decoded RSP_STATE message.
 *
 * @param state        CPU/memory/stack/queue snapshot after the request
 * @param last         the last instruction executed by this request (null if none)
 * @param trace        every instruction executed (only for RUN with includeTrace)
 * @param steps        instructions executed by this request
 * @param serviceNanos time the Core spent <em>executing</em> (excludes encode/IPC) - lets
 *                     the benchmark separate "work" from "IPC overhead"
 */
public record CoreReply(CpuState state, Simulator.StepResult last,
                        List<Simulator.StepResult> trace, int steps, long serviceNanos) {
}
