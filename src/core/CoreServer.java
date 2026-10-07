package com.team.ms51sim.core;

import com.team.ms51sim.ExecContext;
import com.team.ms51sim.Simulator;
import com.team.ms51sim.ipc.CpuState;
import com.team.ms51sim.ipc.Protocol;
import com.team.ms51sim.ipc.StepCodec;
import com.team.ms51sim.ipc.Uds;
import com.team.ms51sim.ipc.Wire;
import com.team.ms51sim.logging.LogClient;
import com.team.ms51sim.logging.LogEvent;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ProtocolException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The <b>Core process</b>: owns the CPU, memory, stack and FIFO queue (the
 * unchanged Week 1-3 {@link Simulator}) and serves requests from the UI.
 *
 * <p><b>Threads:</b> one accept thread, one handler thread per client
 * connection, and the {@link LogClient}'s sender thread. All access to the
 * single {@link Simulator} is {@code synchronized}, so two clients can never
 * interleave half-executed instructions.</p>
 */
public final class CoreServer implements Closeable {

    private final Simulator sim = new Simulator();
    private final LogClient log;                       // may be null (logging disabled)
    private volatile boolean logEachStep = true;
    private final ServerSocketChannel server;
    private final ExecutorService handlers = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "core-handler");
        t.setDaemon(true);
        return t;
    });
    private final Thread acceptor;
    private final CountDownLatch shutdown = new CountDownLatch(1);
    private volatile boolean closed;

    public CoreServer(Path socketFile, LogClient log) throws IOException {
        this.log = log;
        this.server = Uds.listen(socketFile);
        this.acceptor = new Thread(this::acceptLoop, "core-accept");
        this.acceptor.setDaemon(true);
    }

    public void start() { acceptor.start(); }

    public void awaitShutdown() throws InterruptedException { shutdown.await(); }

    private void acceptLoop() {
        while (!closed) {
            try {
                SocketChannel ch = server.accept();
                handlers.execute(() -> serve(ch));
            } catch (ClosedChannelException e) {
                break;
            } catch (IOException e) {
                if (!closed) System.err.println("[core] accept failed: " + e);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  One connection                                                   */
    /* ------------------------------------------------------------------ */

    private void serve(SocketChannel ch) {
        try (Wire w = new Wire(ch)) {
            while (true) {
                Wire.Frame f;
                try {
                    f = w.receive();
                } catch (EOFException | ClosedChannelException e) {
                    return;                                   // client disconnected - normal
                } catch (ProtocolException e) {
                    logErr("protocol error from client: " + e.getMessage());
                    return;                                   // framing is lost; drop the connection
                }
                try {
                    boolean stop = dispatch(w, f);
                    if (stop) { shutdown.countDown(); return; }
                } catch (RuntimeException | IOException e) {
                    logErr("request type " + f.type() + " failed: " + e);
                    w.send(Protocol.RSP_ERROR, Wire.build(o -> o.writeUTF(String.valueOf(e.getMessage()))));
                }
            }
        } catch (IOException e) {
            if (!closed) logErr("connection error: " + e);
        }
    }

    /** @return true if the Core should shut down after this request */
    private boolean dispatch(Wire w, Wire.Frame f) throws IOException {
        DataInputStream in = f.reader();
        switch (f.type()) {
            case Protocol.REQ_LOAD -> {
                int n = in.readInt();
                if (n < 0 || n > 0x2000) throw new IOException("program size out of range: " + n);
                int[] code = new int[n];
                for (int i = 0; i < n; i++) code[i] = in.readUnsignedByte();
                long t0 = System.nanoTime();
                CpuState st;
                synchronized (sim) { sim.load(code); st = CpuState.capture(sim); }
                long dt = System.nanoTime() - t0;
                logInfo("LOAD " + n + " bytes, CPU reset");
                reply(w, st, null, null, 0, dt);
            }
            case Protocol.REQ_RESET -> {
                long t0 = System.nanoTime();
                CpuState st;
                synchronized (sim) { sim.reset(); st = CpuState.capture(sim); }
                long dt = System.nanoTime() - t0;
                logInfo("RESET");
                reply(w, st, null, null, 0, dt);
            }
            case Protocol.REQ_STEP -> {
                long t0 = System.nanoTime();
                Simulator.StepResult r;
                CpuState st;
                synchronized (sim) {
                    r = sim.step();
                    logStep(r);
                    st = CpuState.capture(sim);
                }
                long dt = System.nanoTime() - t0;
                reply(w, st, r, null, r.fetched ? 1 : 0, dt);
            }
            case Protocol.REQ_RUN -> {
                int max = in.readInt();
                boolean withTrace = in.readBoolean();
                long t0 = System.nanoTime();
                List<Simulator.StepResult> trace;
                CpuState st;
                synchronized (sim) {
                    trace = sim.run(max);
                    for (Simulator.StepResult r : trace) logStep(r);
                    st = CpuState.capture(sim);
                }
                long dt = System.nanoTime() - t0;
                Simulator.StepResult last = trace.isEmpty() ? null : trace.get(trace.size() - 1);
                logInfo("RUN finished: " + trace.size() + " instructions"
                        + (st.halted ? " (HLT)" : ""));
                reply(w, st, last, withTrace ? trace : null, trace.size(), dt);
            }
            case Protocol.REQ_STATE -> {
                long t0 = System.nanoTime();
                CpuState st;
                synchronized (sim) { st = CpuState.capture(sim); }
                reply(w, st, null, null, 0, System.nanoTime() - t0);
            }
            case Protocol.REQ_SET_LOGGING -> {
                logEachStep = in.readBoolean();
                logInfo("per-step logging " + (logEachStep ? "ON" : "OFF"));
                w.send(Protocol.RSP_ACK, Wire.EMPTY);
            }
            case Protocol.REQ_LOG -> {
                int level = in.readUnsignedByte();
                String msg = in.readUTF();
                if (log != null) log.log(level, "UI  ", msg);
                w.send(Protocol.RSP_ACK, Wire.EMPTY);
            }
            case Protocol.REQ_PING -> w.send(Protocol.RSP_ACK, Wire.EMPTY);
            case Protocol.REQ_SHUTDOWN -> {
                logInfo("shutdown requested");
                w.send(Protocol.RSP_ACK, Wire.EMPTY);
                return true;
            }
            default -> {
                logErr("unknown request type " + f.type());
                w.send(Protocol.RSP_ERROR, Wire.build(o -> o.writeUTF("unknown request type " + f.type())));
            }
        }
        return false;
    }

    private static void reply(Wire w, CpuState st, Simulator.StepResult last,
                              List<Simulator.StepResult> trace, int steps, long serviceNanos) throws IOException {
        byte[] payload = Wire.build(o -> {
            st.write(o);
            int flags = (last != null ? 1 : 0) | (trace != null ? 2 : 0);
            o.writeByte(flags);
            if (last != null) StepCodec.write(o, last);
            if (trace != null) {
                o.writeInt(trace.size());
                for (Simulator.StepResult r : trace) StepCodec.write(o, r);
            }
            o.writeInt(steps);
            o.writeLong(serviceNanos);
        });
        w.send(Protocol.RSP_STATE, payload);
    }

    /* ------------------------------------------------------------------ */
    /*  Logging helpers                                                  */
    /* ------------------------------------------------------------------ */

    private void logStep(Simulator.StepResult r) {
        if (log == null) return;
        if (!r.fetched) {                                    // stepping a finished / empty program
            if (r.error != null) log.log(LogEvent.WARN, "CORE", r.error);
            return;
        }
        if (r.invalid) {
            log.log(LogEvent.ERROR, "CORE", r.error);
            return;
        }
        if (!logEachStep) {
            if (r.halted) log.log(LogEvent.INFO, "CORE", "program terminated (HLT)");
            return;
        }
        StringBuilder sb = new StringBuilder(96);
        sb.append(ExecContext.hex4(r.address)).append(' ')
          .append(ExecContext.hex2(r.opcode)).append(' ')
          .append(r.mnemonic).append(" | PC ")
          .append(ExecContext.hex4(r.pcBefore)).append("->").append(ExecContext.hex4(r.pcAfter));
        for (ExecContext.Change c : r.changes) {
            sb.append(" | ").append(c.target);
            if (!c.before.isEmpty() || !c.after.isEmpty()) {
                sb.append(' ').append(c.before).append("->").append(c.after);
            }
        }
        log.log(LogEvent.INFO, "CORE", sb.toString());
        if (r.halted) log.log(LogEvent.INFO, "CORE", "program terminated (HLT)");
    }

    private void logInfo(String m) { if (log != null) log.log(LogEvent.INFO, "CORE", m); }
    private void logErr(String m)  { if (log != null) log.log(LogEvent.ERROR, "CORE", m); else System.err.println("[core] " + m); }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try { server.close(); } catch (IOException ignored) { }
        handlers.shutdownNow();
    }
}
