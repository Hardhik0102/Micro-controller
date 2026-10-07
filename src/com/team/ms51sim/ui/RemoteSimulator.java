package com.team.ms51sim.ui;

import com.team.ms51sim.CPU;
import com.team.ms51sim.Simulator;
import com.team.ms51sim.ipc.CoreClient;
import com.team.ms51sim.ipc.CoreReply;
import com.team.ms51sim.logging.LogEvent;

import java.io.IOException;

/**
 * UI-side proxy for the Core process. Each call is one request/response over
 * the socket; the returned CPU snapshot is applied to a local <em>mirror</em>
 * {@link CPU}, which the Swing view renders exactly as it did in Week 3.
 */
public final class RemoteSimulator implements SimulatorBackend, AutoCloseable {

    private final CoreClient client;
    private final CPU mirror = new CPU();
    private boolean finished = true;

    public RemoteSimulator(CoreClient client) {
        this.client = client;
    }

    private void apply(CoreReply r) {
        r.state().applyTo(mirror);
        finished = r.state().finished;
    }

    @Override
    public void load(int[] code) {
        try { apply(client.load(code)); } catch (IOException e) { throw fail(e); }
    }

    @Override
    public void reset() {
        try { apply(client.reset()); } catch (IOException e) { throw fail(e); }
    }

    @Override
    public Simulator.StepResult step() {
        try {
            CoreReply r = client.step();
            apply(r);
            return r.last();
        } catch (IOException e) {
            throw fail(e);
        }
    }

    @Override public CPU cpu() { return mirror; }
    @Override public boolean finished() { return finished; }

    @Override
    public void logError(String message) {
        try { client.log(LogEvent.ERROR, message); } catch (IOException ignored) { }
    }

    private static BackendException fail(IOException e) {
        return new BackendException("Lost connection to the Core process: " + e.getMessage(), e);
    }

    @Override
    public void close() { client.close(); }
}
