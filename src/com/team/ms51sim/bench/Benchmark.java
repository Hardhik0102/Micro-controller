package com.team.ms51sim.bench;

import com.team.ms51sim.Assembler;
import com.team.ms51sim.DemoPrograms;
import com.team.ms51sim.Simulator;
import com.team.ms51sim.ipc.CoreClient;
import com.team.ms51sim.ipc.CoreReply;
import com.team.ms51sim.launcher.ProcessHarness;
import com.team.ms51sim.logging.LogClient;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Standalone (single process) vs multi-process benchmark.
 * <pre>  java -cp out com.team.ms51sim.Main --bench [report.md] [outerLoops] [repetitions]</pre>
 *
 * Metrics: execution time, throughput, per-request IPC round-trip time and
 * IPC overhead, CPU usage of every process, resident memory of every process
 * and process start-up time.
 */
public final class Benchmark {

    private Benchmark() {}

    private record Result(String name, long instr, double medianMs, double cpuPercent,
                          double cpuMs, double logCatchUpMs) {}

    private static long[] lastRtt = new long[0];
    private static long[] lastService = new long[0];

    public static void main(String[] args) throws Exception {
        Path report = args.length > 0 ? Paths.get(args[0]) : null;
        int outer = args.length > 1 ? Integer.parseInt(args[1]) : 40;
        int reps = args.length > 2 ? Integer.parseInt(args[2]) : 5;

        int[] code = new Assembler().assemble(DemoPrograms.benchmarkLoop(outer)).code;
        Simulator probe = new Simulator();
        probe.load(code);
        int instr = probe.run(Integer.MAX_VALUE).size();
        System.out.printf("Benchmark program: %d instructions per run, %d timed repetitions each%n%n", instr, reps);

        List<Result> results = new ArrayList<>();

        /* ---------------- standalone (in this JVM) ---------------- */
        results.add(measure("Standalone  step()", instr, reps, null, null, false, () -> {
            Simulator s = new Simulator();
            s.load(code);
            while (!s.finished()) s.step();
        }));
        results.add(measure("Standalone  run()", instr, reps, null, null, false, () -> {
            Simulator s = new Simulator();
            s.load(code);
            s.run(Integer.MAX_VALUE);
        }));
        long rssStandalone = rssKb(ProcessHandle.current().pid());

        /* ---------------- multi-process ---------------- */
        Path logDir = Files.createTempDirectory("ms51-bench-logs-");
        long t0 = System.nanoTime();
        double startupMs;
        long rssClient, rssCore, rssLogger;
        try (ProcessHarness h = ProcessHarness.start(logDir, false)) {
            startupMs = (System.nanoTime() - t0) / 1e6;
            ProcessHandle core = h.coreProcess().toHandle();
            ProcessHandle logger = h.loggerProcess().toHandle();
            try (CoreClient c = new CoreClient(h.coreSocket(), 3000)) {

                results.add(measure("Multi  STEP per request, logging ON", instr, reps, h, new ProcessHandle[]{core, logger}, true, () -> {
                    stepAll(c, code, true, instr);
                }));
                results.add(measure("Multi  STEP per request, logging OFF", instr, reps, h, new ProcessHandle[]{core, logger}, false, () -> {
                    stepAll(c, code, false, instr);
                }));
                long[] rttOff = lastRtt, svcOff = lastService;
                results.add(measure("Multi  RUN (1 request), logging ON", instr, reps, h, new ProcessHandle[]{core, logger}, true, () -> {
                    c.setLogging(true);
                    c.load(code);
                    c.run(Integer.MAX_VALUE, false);
                }));
                results.add(measure("Multi  RUN (1 request), logging OFF", instr, reps, h, new ProcessHandle[]{core, logger}, false, () -> {
                    c.setLogging(false);
                    c.load(code);
                    c.run(Integer.MAX_VALUE, false);
                }));
                results.add(measure("Multi  RUN + full trace returned, logging OFF", instr, reps, h, new ProcessHandle[]{core, logger}, false, () -> {
                    c.setLogging(false);
                    c.load(code);
                    c.run(Integer.MAX_VALUE, true);
                }));

                // latency distribution of STEP requests (logging OFF run kept above)
                lastRtt = rttOff;
                lastService = svcOff;
            }
            rssClient = rssKb(ProcessHandle.current().pid());
            rssCore = rssKb(core.pid());
            rssLogger = rssKb(logger.pid());
        }

        String md = render(results, instr, reps, startupMs, rssStandalone, rssClient, rssCore, rssLogger);
        System.out.println(md);
        if (report != null) {
            Files.writeString(report, md);
            System.out.println("Report written to " + report.toAbsolutePath());
        }
    }

    /* ------------------------------------------------------------------ */

    private static void stepAll(CoreClient c, int[] code, boolean logging, int expected) throws Exception {
        c.setLogging(logging);
        c.load(code);
        long[] rtt = new long[expected + 8];
        long[] svc = new long[expected + 8];
        int n = 0;
        while (true) {
            CoreReply r = c.step();
            if (n < rtt.length) { rtt[n] = c.lastRttNanos(); svc[n] = r.serviceNanos(); n++; }
            if (r.state().finished) break;
        }
        lastRtt = Arrays.copyOf(rtt, n);
        lastService = Arrays.copyOf(svc, n);
    }

    private interface Body { void run() throws Exception; }

    private static Result measure(String name, long instr, int reps, ProcessHarness h,
                                  ProcessHandle[] others, boolean waitForLogger, Body body) throws Exception {
        for (int w = 0; w < 5; w++) body.run();                  // equal warm-up for every configuration (JIT)
        if (waitForLogger) drainLogger(h);
        long ownCpu0 = ownCpuMs();
        long othersCpu0 = cpuMs(others);
        double[] ms = new double[reps];
        double catchUp = 0;
        long wallTotal = 0;
        for (int i = 0; i < reps; i++) {
            long t = System.nanoTime();
            body.run();
            long dt = System.nanoTime() - t;
            ms[i] = dt / 1e6;
            wallTotal += dt;
            if (waitForLogger) catchUp += drainLogger(h);
        }
        long cpu = (ownCpuMs() - ownCpu0) + (cpuMs(others) - othersCpu0);
        Arrays.sort(ms);
        double median = ms[reps / 2];
        double cpuPct = 100.0 * cpu / (wallTotal / 1e6 + (waitForLogger ? catchUp : 0));
        Result r = new Result(name, instr, median, cpuPct, (double) cpu / reps, catchUp / reps);
        System.out.printf("%-48s median %8.1f ms   %6.2f us/instr   CPU %.0f%%%n",
                name, median, median * 1000 / instr, cpuPct);
        return r;
    }

    /** Wait until the Logger has stopped receiving; returns how long that took (ms). */
    private static double drainLogger(ProcessHarness h) throws Exception {
        long t0 = System.nanoTime();
        long prev = -1;
        int stable = 0;
        while (stable < 3) {
            long total = LogClient.queryStats(h.logSocket()).total();
            stable = (total == prev) ? stable + 1 : 0;
            prev = total;
            Thread.sleep(15);
        }
        return (System.nanoTime() - t0) / 1e6 - 45;              // minus the 3 stable polls we waited
    }

    private static long ownCpuMs() {
        var os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean x) return x.getProcessCpuTime() / 1_000_000;
        return 0;
    }

    private static long cpuMs(ProcessHandle[] hs) {
        if (hs == null) return 0;
        long sum = 0;
        for (ProcessHandle p : hs) sum += p.info().totalCpuDuration().map(Duration::toMillis).orElse(0L);
        return sum;
    }

    /** Resident set size in kB from /proc (Linux); -1 if unavailable. */
    private static long rssKb(long pid) {
        try {
            for (String line : Files.readAllLines(Paths.get("/proc/" + pid + "/status"))) {
                if (line.startsWith("VmRSS:")) return Long.parseLong(line.replaceAll("[^0-9]", ""));
            }
        } catch (Exception ignored) { }
        return -1;
    }

    /* ------------------------------------------------------------------ */

    private static double pct(long[] sorted, double p) {
        return sorted[(int) Math.min(sorted.length - 1, Math.floor(p * sorted.length))] / 1000.0;   // ns -> us
    }

    private static String render(List<Result> rs, long instr, int reps, double startupMs,
                                 long rssStandalone, long rssClient, long rssCore, long rssLogger) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Environment\n\n");
        sb.append("- Date: ").append(LocalDateTime.now().withNano(0)).append('\n');
        sb.append("- Java: ").append(System.getProperty("java.version")).append(" (").append(System.getProperty("java.vm.name")).append(")\n");
        sb.append("- OS: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
          .append(", ").append(Runtime.getRuntime().availableProcessors()).append(" CPU core(s) available\n");
        sb.append("- Workload: ").append(instr).append(" instructions per run, median of ").append(reps).append(" runs after 5 warm-up runs\n\n");

        sb.append("### 1. Execution time and CPU usage\n\n");
        sb.append("| Configuration | Median time (ms) | us / instruction | Instructions / s | CPU time / run (ms) | CPU usage* | Logger catch-up after run (ms) |\n");
        sb.append("|---|---:|---:|---:|---:|---:|---:|\n");
        for (Result r : rs) {
            sb.append(String.format("| %s | %.1f | %.2f | %,.0f | %.0f | %.0f%% | %s |%n",
                    r.name(), r.medianMs(), r.medianMs() * 1000 / r.instr(), r.instr() / (r.medianMs() / 1000),
                    r.cpuMs(), r.cpuPercent(), r.logCatchUpMs() > 0 ? String.format("%.0f", r.logCatchUpMs()) : "-"));
        }
        sb.append("\n\\* CPU usage = (CPU time of UI-client + Core + Logger processes) / elapsed wall time; 100% = one core fully busy. ")
          .append("On a multi-core machine the three processes can run in parallel, so values above 100% are possible.\n\n");

        sb.append("### 2. IPC latency per STEP request (logging OFF run)\n\n");
        long[] rtt = lastRtt.clone();
        long[] svc = lastService.clone();
        Arrays.sort(rtt);
        Arrays.sort(svc);
        double rttMean = Arrays.stream(rtt).average().orElse(0) / 1000.0;
        double svcMean = Arrays.stream(svc).average().orElse(0) / 1000.0;
        sb.append("| Metric | Value (us) |\n|---|---:|\n");
        sb.append(String.format("| Round-trip time (send request -> reply received), mean | %.1f |%n", rttMean));
        sb.append(String.format("| Round-trip time, median (p50) | %.1f |%n", pct(rtt, 0.50)));
        sb.append(String.format("| Round-trip time, p99 | %.1f |%n", pct(rtt, 0.99)));
        sb.append(String.format("| Core execution time inside the request (fetch/decode/execute + state capture), mean | %.1f |%n", svcMean));
        sb.append(String.format("| **IPC overhead per request** (round-trip minus Core execution: serialisation + 2 context switches + socket copy), mean | **%.1f** |%n%n", rttMean - svcMean));

        sb.append("### 3. Memory (resident set size)\n\n");
        sb.append("| Process | RSS (MB) |\n|---|---:|\n");
        sb.append(String.format("| Standalone simulator (1 JVM, UI-less benchmark) | %.1f |%n", rssStandalone / 1024.0));
        sb.append(String.format("| Multi-process: UI/client JVM | %.1f |%n", rssClient / 1024.0));
        sb.append(String.format("| Multi-process: Core JVM | %.1f |%n", rssCore / 1024.0));
        sb.append(String.format("| Multi-process: Logger JVM | %.1f |%n", rssLogger / 1024.0));
        sb.append(String.format("| **Multi-process total** | **%.1f** |%n%n", (rssClient + rssCore + rssLogger) / 1024.0));

        sb.append("### 4. Process start-up\n\n");
        sb.append(String.format("Starting Logger + Core as separate JVMs and waiting until both sockets accept connections took **%.0f ms** (the standalone simulator has no such cost).%n", startupMs));
        return sb.toString();
    }
}
