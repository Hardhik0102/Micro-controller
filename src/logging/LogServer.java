package com.team.ms51sim.logging;

import com.team.ms51sim.ipc.Protocol;
import com.team.ms51sim.ipc.Uds;
import com.team.ms51sim.ipc.Wire;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The Logging process' server.
 *
 * <p><b>Threads:</b> an <em>accept</em> thread, one <em>handler</em> thread per
 * connected client (reads frames, puts events on the queue) and ONE
 * <em>writer</em> thread that owns the log files - the classic
 * producer/consumer pattern, so files are never written concurrently.</p>
 *
 * <p>Files (in {@code logDir}): {@code ms51sim.log} gets every event,
 * {@code ms51sim-errors.log} gets only WARN and ERROR events.</p>
 */
public final class LogServer implements Closeable {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    /** Marker placed on the queue; the writer counts it down after flushing the files. */
    private record Barrier(CountDownLatch latch) {}
    private static final Object STOP = new Object();

    private final ServerSocketChannel server;
    private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
    private final ExecutorService handlers = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "log-handler");
        t.setDaemon(true);
        return t;
    });
    private final Thread acceptor;
    private final Thread writer;
    private final CountDownLatch shutdown = new CountDownLatch(1);
    private final Path logFile;
    private final Path errorFile;
    private volatile boolean closed;

    // written only by the writer thread; read after a Barrier (latch gives happens-before)
    private long total, nDebug, nInfo, nWarn, nError;

    public LogServer(Path socketFile, Path logDir) throws IOException {
        Files.createDirectories(logDir);
        this.logFile = logDir.resolve("ms51sim.log");
        this.errorFile = logDir.resolve("ms51sim-errors.log");
        this.server = Uds.listen(socketFile);
        this.writer = new Thread(this::writerLoop, "log-writer");
        this.writer.setDaemon(true);
        this.acceptor = new Thread(this::acceptLoop, "log-accept");
        this.acceptor.setDaemon(true);
    }

    public Path logFile() { return logFile; }
    public Path errorFile() { return errorFile; }

    public void start() {
        writer.start();
        acceptor.start();
    }

    public void awaitShutdown() throws InterruptedException {
        shutdown.await();
    }

    /* ------------------------------ accept / handle ------------------------------ */

    private void acceptLoop() {
        while (!closed) {
            try {
                SocketChannel ch = server.accept();
                handlers.execute(() -> serve(ch));
            } catch (ClosedChannelException e) {
                break;
            } catch (IOException e) {
                if (!closed) System.err.println("[logger] accept failed: " + e);
            }
        }
    }

    private void serve(SocketChannel ch) {
        try (Wire w = new Wire(ch)) {
            while (true) {
                Wire.Frame f;
                try {
                    f = w.receive();
                } catch (EOFException | ClosedChannelException e) {
                    return;
                }
                switch (f.type()) {
                    case Protocol.LOG_EVENT -> queue.add(LogEvent.read(f.reader()));
                    case Protocol.LOG_STATS_REQ -> {
                        awaitFlush();
                        final long[] c = {total, nDebug, nInfo, nWarn, nError};
                        w.send(Protocol.LOG_STATS_RSP, Wire.build(o -> {
                            for (long v : c) o.writeLong(v);
                        }));
                    }
                    case Protocol.LOG_SHUTDOWN -> {
                        awaitFlush();
                        w.send(Protocol.RSP_ACK, Wire.EMPTY);
                        shutdown.countDown();
                        return;
                    }
                    default -> w.send(Protocol.RSP_ERROR,
                            Wire.build(o -> o.writeUTF("logger: unknown message type " + f.type())));
                }
            }
        } catch (IOException e) {
            if (!closed) System.err.println("[logger] client error: " + e);
        }
    }

    /** Block until the writer has written + flushed everything queued before this call. */
    private void awaitFlush() {
        CountDownLatch latch = new CountDownLatch(1);
        queue.add(new Barrier(latch));
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /* ------------------------------ writer thread ------------------------------ */

    private void writerLoop() {
        try (BufferedWriter all = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8);
             BufferedWriter errs = Files.newBufferedWriter(errorFile, StandardCharsets.UTF_8)) {
            while (true) {
                Object o = queue.take();
                if (o == STOP) break;
                if (o instanceof Barrier b) {
                    all.flush();
                    errs.flush();
                    b.latch().countDown();
                    continue;
                }
                LogEvent e = (LogEvent) o;
                String line = TS.format(Instant.ofEpochMilli(e.epochMillis()))
                        + " [" + LogEvent.levelName(e.level()) + "] [" + e.source() + "] "
                        + e.message();
                all.write(line);
                all.newLine();
                if (e.level() >= LogEvent.WARN) {
                    errs.write(line);
                    errs.newLine();
                }
                total++;
                switch (e.level()) {
                    case LogEvent.DEBUG -> nDebug++;
                    case LogEvent.INFO -> nInfo++;
                    case LogEvent.WARN -> nWarn++;
                    default -> nError++;
                }
                if (queue.isEmpty()) {       // flush once per burst, not once per line
                    all.flush();
                    errs.flush();
                }
            }
        } catch (IOException e) {
            System.err.println("[logger] cannot write log file: " + e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        queue.add(STOP);
        try { writer.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        try { server.close(); } catch (IOException ignored) { }
        handlers.shutdownNow();
    }
}
