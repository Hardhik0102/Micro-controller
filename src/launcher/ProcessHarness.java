package com.team.ms51sim.launcher;

import com.team.ms51sim.ipc.CoreClient;
import com.team.ms51sim.logging.LogClient;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Team-leader integration code: starts the Logger and Core as <b>separate JVM
 * processes</b> (so they really are three OS processes with their own PIDs and
 * address spaces), waits until their sockets exist, and shuts them down again.
 * Used by the launcher, the integration tests and the benchmark.
 */
public final class ProcessHarness implements Closeable {

    private final Path sockDir;
    private final Path logDir;
    private final boolean inheritIO;
    private Process logger;
    private Process core;

    private ProcessHarness(Path sockDir, Path logDir, boolean inheritIO) {
        this.sockDir = sockDir;
        this.logDir = logDir;
        this.inheritIO = inheritIO;
    }

    /** Start Logger first, then Core (Core connects to the Logger's socket). */
    public static ProcessHarness start(Path logDir, boolean inheritIO) throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory("ms51sim-");
        ProcessHarness h = new ProcessHarness(dir, logDir, inheritIO);
        try {
            h.logger = h.spawn("--logger");
            h.awaitSocket(h.logSocket(), h.logger);
            h.core = h.spawn("--core");
            h.awaitSocket(h.coreSocket(), h.core);
        } catch (IOException | InterruptedException | RuntimeException e) {
            h.close();
            throw e;
        }
        return h;
    }

    public Path sockDir()    { return sockDir; }
    public Path logDir()     { return logDir; }
    public Path coreSocket() { return sockDir.resolve("core.sock"); }
    public Path logSocket()  { return sockDir.resolve("log.sock"); }
    public Process loggerProcess() { return logger; }
    public Process coreProcess()   { return core; }

    /** Start another process of this application (e.g. {@code --ui}) sharing the same socket dir. */
    public Process spawn(String role) throws IOException {
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java,
                "-Dms51.sockdir=" + sockDir,
                "-Dms51.logdir=" + logDir,
                "-Xshare:auto",
                "-cp", System.getProperty("java.class.path"),
                "com.team.ms51sim.Main", role);
        if (inheritIO) {
            pb.inheritIO();
        } else {
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        }
        return pb.start();
    }

    private void awaitSocket(Path sock, Process p) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!Files.exists(sock)) {
            if (!p.isAlive()) throw new IOException("process for " + sock.getFileName() + " exited early (code " + p.exitValue() + ")");
            if (System.currentTimeMillis() > deadline) throw new IOException("timeout waiting for " + sock);
            Thread.sleep(25);
        }
    }

    @Override
    public void close() {
        // ask politely first, then force
        try (CoreClient c = new CoreClient(coreSocket(), 0)) { c.shutdownCore(); } catch (Exception ignored) { }
        try { LogClient.shutdownLogger(logSocket()); } catch (Exception ignored) { }
        for (Process p : new Process[]{core, logger}) {
            if (p == null) continue;
            try {
                if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly();
            } catch (InterruptedException e) {
                p.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        try (Stream<Path> s = Files.walk(sockDir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) { }
    }
}
