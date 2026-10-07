package com.team.ms51sim.logging;

import com.team.ms51sim.ipc.IpcException;
import com.team.ms51sim.ipc.Protocol;
import com.team.ms51sim.ipc.Uds;
import com.team.ms51sim.ipc.Wire;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Used by the Core to send log events to the Logger process.
 *
 * <p><b>Threading:</b> {@link #log} only puts the event on a bounded
 * {@link BlockingQueue} (producer = the Core's request thread). A dedicated
 * <em>sender thread</em> (consumer) drains the queue, writes the frames to the
 * socket and flushes once per batch. So logging never does socket I/O on the
 * instruction-execution path, and the Core keeps working - events are just
 * counted as dropped - when the Logger process is not running.</p>
 */
public final class LogClient implements Closeable {

    private final Path socket;
    private final BlockingQueue<LogEvent> queue = new ArrayBlockingQueue<>(50_000);
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final Thread sender;
    private volatile boolean running = true;
    private Wire wire;                       // owned by the sender thread
    private long lastConnectAttempt;

    public LogClient(Path socket) {
        this.socket = socket;
        this.sender = new Thread(this::senderLoop, "log-sender");
        this.sender.setDaemon(true);
        this.sender.start();
    }

    public long sent()    { return sent.get(); }
    public long dropped() { return dropped.get(); }

    public void log(int level, String source, String message) {
        LogEvent e = LogEvent.now(level, source, message);
        pending.incrementAndGet();
        try {
            queue.put(e);                    // blocks only if 50 000 events are queued (back-pressure)
        } catch (InterruptedException ie) {
            pending.decrementAndGet();
            Thread.currentThread().interrupt();
        }
    }

    private void senderLoop() {
        List<LogEvent> batch = new ArrayList<>(256);
        while (running || !queue.isEmpty()) {
            try {
                LogEvent first = queue.poll(100, TimeUnit.MILLISECONDS);
                if (first == null) continue;
                batch.clear();
                batch.add(first);
                queue.drainTo(batch, 255);
                transmit(batch);
            } catch (InterruptedException e) {
                break;
            }
        }
        if (wire != null) wire.close();
    }

    private void transmit(List<LogEvent> batch) {
        try {
            if (wire == null) connectIfDue();
            if (wire == null) { drop(batch); return; }
            try {
                for (LogEvent e : batch) wire.sendNoFlush(Protocol.LOG_EVENT, Wire.build(e::write));
                wire.flush();
                sent.addAndGet(batch.size());
                pending.addAndGet(-batch.size());
            } catch (IOException io) {      // Logger went away mid-run
                wire.close();
                wire = null;
                drop(batch);
            }
        } catch (IOException ioe) {
            drop(batch);
        }
    }

    private void connectIfDue() throws IOException {
        long now = System.currentTimeMillis();
        if (now - lastConnectAttempt < 500) return;      // do not hammer a missing socket
        lastConnectAttempt = now;
        try {
            wire = new Wire(Uds.connect(socket, 0));
        } catch (IOException e) {
            wire = null;
        }
    }

    private void drop(List<LogEvent> batch) {
        dropped.addAndGet(batch.size());
        pending.addAndGet(-batch.size());
    }

    /** Wait until every queued event has been written to the socket (or dropped). */
    public boolean flush(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (pending.get() > 0 && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        return pending.get() == 0;
    }

    @Override
    public void close() {
        running = false;
        try { sender.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /* ---------------- one-shot admin calls (own connection) ---------------- */

    /** Ask the Logger for its counters; it flushes everything it has received first. */
    public static LogStats queryStats(Path socket) throws IOException {
        try (Wire w = new Wire(Uds.connect(socket, 2000))) {
            w.send(Protocol.LOG_STATS_REQ, Wire.EMPTY);
            Wire.Frame f = w.receive();
            if (f.type() == Protocol.RSP_ERROR) throw new IpcException(f.reader().readUTF());
            DataInputStream in = f.reader();
            return new LogStats(in.readLong(), in.readLong(), in.readLong(), in.readLong(), in.readLong());
        }
    }

    public static void shutdownLogger(Path socket) throws IOException {
        try (Wire w = new Wire(Uds.connect(socket, 500))) {
            w.send(Protocol.LOG_SHUTDOWN, Wire.EMPTY);
            w.receive();
        }
    }
}
