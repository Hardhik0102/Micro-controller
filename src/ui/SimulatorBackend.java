package com.team.ms51sim.ui;

import com.team.ms51sim.CPU;
import com.team.ms51sim.Simulator;

/**
 * What the UI needs from "the simulator". Two implementations:
 * {@link LocalBackend} (Week 3 single-process behaviour) and
 * {@link RemoteSimulator} (talks to the Core process over a Unix domain socket).
 * Because the UI only knows this interface, the same Swing code runs in both modes.
 */
public interface SimulatorBackend {

    /** Raised when the backend (e.g. the Core process) cannot be reached. */
    class BackendException extends RuntimeException {
        public BackendException(String msg, Throwable cause) { super(msg, cause); }
    }

    void load(int[] machineCode);
    void reset();
    Simulator.StepResult step();

    /** CPU state to display (for a remote backend: a local mirror of the Core's CPU). */
    CPU cpu();
    boolean finished();

    /** Forward a UI-side error to the logging process (no-op for the local backend). */
    default void logError(String message) { }
}
