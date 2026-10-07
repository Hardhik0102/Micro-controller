package com.team.ms51sim.ipc;

import com.team.ms51sim.Simulator;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Client side of the UI &lt;-&gt; Core protocol: one blocking request/response
 * call per method. Thread-safe (calls are serialised).
 */
public final class CoreClient implements Closeable {

    private final Wire wire;
    private long lastRttNanos;

    public CoreClient(Path coreSocket, long connectTimeoutMs) throws IOException {
        this.wire = new Wire(Uds.connect(coreSocket, connectTimeoutMs));
    }

    public long lastRttNanos() { return lastRttNanos; }

    private synchronized Wire.Frame call(int type, byte[] payload) throws IOException {
        long t0 = System.nanoTime();
        wire.send(type, payload);
        Wire.Frame f = wire.receive();
        lastRttNanos = System.nanoTime() - t0;
        if (f.type() == Protocol.RSP_ERROR) {
            throw new IpcException(f.reader().readUTF());
        }
        return f;
    }

    private static CoreReply parseState(Wire.Frame f) throws IOException {
        if (f.type() != Protocol.RSP_STATE) {
            throw new IpcException("unexpected reply type " + f.type());
        }
        DataInputStream in = f.reader();
        CpuState state = CpuState.read(in);
        int flags = in.readUnsignedByte();
        Simulator.StepResult last = (flags & 1) != 0 ? StepCodec.read(in) : null;
        List<Simulator.StepResult> trace = null;
        if ((flags & 2) != 0) {
            int n = in.readInt();
            trace = new ArrayList<>(n);
            for (int i = 0; i < n; i++) trace.add(StepCodec.read(in));
        }
        int steps = in.readInt();
        long service = in.readLong();
        return new CoreReply(state, last, trace, steps, service);
    }

    public CoreReply load(int[] code) throws IOException {
        byte[] p = Wire.build(o -> {
            o.writeInt(code.length);
            for (int c : code) o.writeByte(c);
        });
        return parseState(call(Protocol.REQ_LOAD, p));
    }

    public CoreReply reset() throws IOException {
        return parseState(call(Protocol.REQ_RESET, Wire.EMPTY));
    }

    public CoreReply step() throws IOException {
        return parseState(call(Protocol.REQ_STEP, Wire.EMPTY));
    }

    public CoreReply run(int maxSteps, boolean includeTrace) throws IOException {
        byte[] p = Wire.build(o -> { o.writeInt(maxSteps); o.writeBoolean(includeTrace); });
        return parseState(call(Protocol.REQ_RUN, p));
    }

    public CoreReply state() throws IOException {
        return parseState(call(Protocol.REQ_STATE, Wire.EMPTY));
    }

    public void setLogging(boolean perStep) throws IOException {
        call(Protocol.REQ_SET_LOGGING, Wire.build(o -> o.writeBoolean(perStep)));
    }

    /** Ask the Core to forward a UI-side message (e.g. an assembly error) to the Logger. */
    public void log(int level, String message) throws IOException {
        call(Protocol.REQ_LOG, Wire.build(o -> { o.writeByte(level); o.writeUTF(message); }));
    }

    /** Round-trip time of an empty request, in nanoseconds. */
    public long ping() throws IOException {
        call(Protocol.REQ_PING, Wire.EMPTY);
        return lastRttNanos;
    }

    /** Send a request type the Core does not know (used by the protocol tests). */
    public Wire.Frame callRaw(int type, byte[] payload) throws IOException {
        return call(type, payload);
    }

    public void shutdownCore() throws IOException {
        call(Protocol.REQ_SHUTDOWN, Wire.EMPTY);
    }

    @Override
    public void close() { wire.close(); }
}
