package com.team.ms51sim.tests;

import com.team.ms51sim.Assembler;
import com.team.ms51sim.DemoPrograms;
import com.team.ms51sim.Simulator;
import com.team.ms51sim.TraceFormatter;
import com.team.ms51sim.core.CoreServer;
import com.team.ms51sim.ipc.CoreClient;
import com.team.ms51sim.ipc.CoreReply;
import com.team.ms51sim.ipc.CpuState;
import com.team.ms51sim.ipc.IpcException;
import com.team.ms51sim.ipc.Protocol;
import com.team.ms51sim.ipc.StepCodec;
import com.team.ms51sim.ipc.Uds;
import com.team.ms51sim.ipc.Wire;
import com.team.ms51sim.launcher.ProcessHarness;
import com.team.ms51sim.logging.LogClient;
import com.team.ms51sim.logging.LogServer;
import com.team.ms51sim.logging.LogStats;

import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * IPC test cases (Week 4). No framework needed:
 * <pre>  java -cp out com.team.ms51sim.tests.IpcTests [results.md]</pre>
 * Each case prints  ID | description | expected | actual | PASS/FAIL  (the format of the Week 2 test record).
 */
public final class IpcTests {

    private record Row(String id, String what, String expected, String actual, boolean pass) {}

    private static final List<Row> rows = new ArrayList<>();

    private static void test(String id, String what, String expected, Callable<String> actual) {
        String got;
        try {
            got = actual.call();
        } catch (Throwable t) {
            got = "EXCEPTION " + t;
        }
        boolean ok = got.equals(expected);
        rows.add(new Row(id, what, expected, got, ok));
        System.out.printf("%-7s %-4s %s%n", id, ok ? "PASS" : "FAIL", what);
        if (!ok) System.out.println("          expected: " + expected + "\n          actual  : " + got);
    }

    private static int[] demoCode() {
        return new Assembler().assemble(DemoPrograms.WEEK3_DEMO).code;
    }

    /** Poll until the condition holds (the Logger receives events asynchronously over its own socket). */
    private static boolean eventually(Callable<Boolean> cond, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (true) {
            if (cond.call()) return true;
            if (System.currentTimeMillis() > end) return false;
            Thread.sleep(25);
        }
    }

    private static String hex(int v) { return String.format("%02XH", v); }

    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("ms51-ipctest-");
        Path logDir = tmp.resolve("logs");
        Path logSock = tmp.resolve("log.sock");
        Path coreSock = tmp.resolve("core.sock");
        int[] demo = demoCode();

        // in-process servers (separate threads, real sockets) for the protocol-level tests
        LogServer logServer = new LogServer(logSock, logDir);
        logServer.start();
        LogClient logClient = new LogClient(logSock);
        CoreServer core = new CoreServer(coreSock, logClient);
        core.start();

        try (CoreClient c = new CoreClient(coreSock, 2000)) {

            /* ---------- codecs ---------- */
            test("IPC-01", "StepResult survives encode/decode (all 16 demo instructions)", "16 identical",
                    () -> {
                        Simulator s = new Simulator();
                        s.load(demo);
                        int same = 0;
                        for (Simulator.StepResult r : s.run(100)) {
                            byte[] b = Wire.build(o -> StepCodec.write(o, r));
                            Simulator.StepResult back = StepCodec.read(new DataInputStream(new java.io.ByteArrayInputStream(b)));
                            if (TraceFormatter.format(r).equals(TraceFormatter.format(back))) same++;
                        }
                        return same + " identical";
                    });

            test("IPC-02", "CpuState (regs, RAM, queue) survives encode/decode", "true",
                    () -> {
                        Simulator s = new Simulator();
                        s.load(demo);
                        s.run(100);
                        CpuState st = CpuState.capture(s);
                        byte[] b = Wire.build(st::write);
                        return String.valueOf(st.equals(CpuState.read(new DataInputStream(new java.io.ByteArrayInputStream(b)))));
                    });

            /* ---------- core functionality over IPC ---------- */
            test("IPC-03", "LOAD returns reset state: PC=0000H, length 26, not finished", "PC=0 len=26 finished=false",
                    () -> {
                        CoreReply r = c.load(demo);
                        return "PC=" + r.state().pc + " len=" + r.state().programLength + " finished=" + r.state().finished;
                    });

            test("IPC-04", "Remote STEP == local step for every instruction (trace text + CPU state)", "16/16 equal",
                    () -> {
                        Simulator local = new Simulator();
                        local.load(demo);
                        c.load(demo);
                        int equal = 0, n = 0;
                        while (!local.finished()) {
                            Simulator.StepResult lr = local.step();
                            CoreReply rr = c.step();
                            n++;
                            if (TraceFormatter.format(lr).equals(TraceFormatter.format(rr.last()))
                                    && CpuState.capture(local).equals(rr.state())) equal++;
                        }
                        return equal + "/" + n + " equal";
                    });

            test("IPC-05", "Remote RUN == local run (instruction count, trace, final state)", "true",
                    () -> {
                        Simulator local = new Simulator();
                        local.load(demo);
                        List<Simulator.StepResult> lt = local.run(1000);
                        c.load(demo);
                        CoreReply r = c.run(1000, true);
                        boolean traceEq = lt.size() == r.trace().size();
                        for (int i = 0; traceEq && i < lt.size(); i++)
                            traceEq = TraceFormatter.format(lt.get(i)).equals(TraceFormatter.format(r.trace().get(i)));
                        return String.valueOf(traceEq && r.steps() == lt.size() && CpuState.capture(local).equals(r.state()));
                    });

            test("IPC-06", "FIFO order across IPC: RAM[31H]=11H, RAM[32H]=22H, queue=[33H]", "11H 22H [33H]",
                    () -> {
                        c.load(demo);
                        CpuState s = c.run(1000, false).state();
                        return hex(s.ram[0x31]) + " " + hex(s.ram[0x32]) + " " + Arrays.stream(s.queue).mapToObj(IpcTests::hex).toList().toString().replace(", ", " ");
                    });

            test("IPC-07", "Stack across IPC: after PUSH SP=08H & RAM[08H]=7EH; after POP SP=07H", "SP=08H RAM=7EH | SP=07H",
                    () -> {
                        c.load(demo);
                        c.step(); c.step();
                        CpuState afterPush = c.step().state();
                        c.step(); c.step();
                        CpuState afterPop = c.state().state();
                        return "SP=" + hex(afterPush.sp) + " RAM=" + hex(afterPush.ram[8]) + " | SP=" + hex(afterPop.sp);
                    });

            test("IPC-08", "RESET restores power-on state (PC=0, ACC=0, RAM[30H]=0, queue empty)", "PC=0 ACC=0 RAM30=0 q=0",
                    () -> {
                        c.load(demo);
                        c.run(1000, false);
                        CpuState s = c.reset().state();
                        return "PC=" + s.pc + " ACC=" + s.acc + " RAM30=" + s.ram[0x30] + " q=" + s.queue.length;
                    });

            /* ---------- error handling ---------- */
            test("IPC-09", "Unknown opcode A8H is reported as invalid (no crash)", "invalid=true error=Unknown opcode A8H at 0000H",
                    () -> {
                        CoreReply r = c.load(new int[]{0xA8}).state() != null ? c.step() : null;
                        return "invalid=" + r.last().invalid + " error=" + r.last().error;
                    });

            test("IPC-10", "STEP after HLT returns 'CPU is halted' and CPU stays halted", "error=CPU is halted halted=true",
                    () -> {
                        c.load(demo);
                        c.run(1000, false);
                        CoreReply r = c.step();
                        return "error=" + r.last().error + " halted=" + r.state().halted;
                    });

            test("IPC-11", "Unknown request type -> RSP_ERROR, connection still usable", "IpcException then ping OK",
                    () -> {
                        String first;
                        try { c.callRaw(250, Wire.EMPTY); first = "no error"; }
                        catch (IpcException e) { first = "IpcException"; }
                        c.ping();
                        return first + " then ping OK";
                    });

            test("IPC-12", "Corrupt frame (absurd length) closes that connection only; Core keeps serving", "dropped, new client OK",
                    () -> {
                        String r1;
                        try (Wire raw = new Wire(Uds.connect(coreSock, 1000))) {
                            raw.rawOut().writeInt(0x7FFFFFFF);
                            raw.rawOut().writeByte(Protocol.REQ_STEP);
                            raw.rawOut().flush();
                            try { raw.receive(); r1 = "NOT dropped"; } catch (java.io.IOException e) { r1 = "dropped"; }
                        }
                        try (CoreClient c2 = new CoreClient(coreSock, 1000)) { c2.ping(); }
                        return r1 + ", new client OK";
                    });

            test("IPC-13", "Client disconnects abruptly mid-session; Core keeps its state for the next client", "PC=2 kept",
                    () -> {
                        c.load(demo);
                        try (CoreClient tmpClient = new CoreClient(coreSock, 1000)) { tmpClient.step(); }   // closes without goodbye
                        Thread.sleep(50);
                        return "PC=" + c.state().state().pc + " kept";
                    });

            test("IPC-14", "4 concurrent clients x 100 STEPs == 400 sequential local steps", "true",
                    () -> {
                        int[] loop = new Assembler().assemble(DemoPrograms.benchmarkLoop(40)).code;
                        c.load(loop);
                        Thread[] ts = new Thread[4];
                        for (int i = 0; i < ts.length; i++) {
                            ts[i] = new Thread(() -> {
                                try (CoreClient cc = new CoreClient(coreSock, 1000)) {
                                    for (int k = 0; k < 100; k++) cc.step();
                                } catch (Exception e) { throw new RuntimeException(e); }
                            });
                            ts[i].start();
                        }
                        for (Thread t : ts) t.join();
                        Simulator local = new Simulator();
                        local.load(loop);
                        for (int k = 0; k < 400; k++) local.step();
                        return String.valueOf(CpuState.capture(local).equals(c.state().state()));
                    });

            /* ---------- logging process ---------- */
            test("IPC-15", "No log loss: Logger total == events the Core sent", "equal",
                    () -> {
                        c.load(demo);
                        c.run(1000, false);
                        logClient.flush(3000);
                        boolean eq = eventually(() -> LogClient.queryStats(logSock).total() == logClient.sent(), 3000);
                        return eq && logClient.dropped() == 0 ? "equal"
                                : "logger=" + LogClient.queryStats(logSock).total() + " sent=" + logClient.sent() + " dropped=" + logClient.dropped();
                    });

            test("IPC-16", "UI-side error is forwarded UI -> Core -> Logger as an ERROR line", "found in errors file",
                    () -> {
                        c.log(3, "unit-test assembly error XYZ");
                        logClient.flush(3000);
                        boolean found = eventually(() -> {
                            LogClient.queryStats(logSock);                   // barrier: forces a file flush
                            return Files.readAllLines(logServer.errorFile()).stream()
                                    .anyMatch(l -> l.contains("[ERROR]") && l.contains("[UI  ]") && l.contains("XYZ"));
                        }, 3000);
                        return found ? "found in errors file" : "missing";
                    });

            test("IPC-17", "Invalid opcode is logged as ERROR; errors file holds only WARN/ERROR", "ok",
                    () -> {
                        c.load(new int[]{0xA8});
                        c.step();
                        logClient.flush(3000);
                        boolean hasUnknown = eventually(() -> {
                            LogClient.queryStats(logSock);
                            return Files.readAllLines(logServer.errorFile()).stream().anyMatch(l -> l.contains("Unknown opcode A8H"));
                        }, 3000);
                        boolean onlyBad = Files.readAllLines(logServer.errorFile()).stream()
                                .allMatch(l -> l.contains("[ERROR]") || l.contains("[WARN ]"));
                        return hasUnknown && onlyBad ? "ok" : "hasUnknown=" + hasUnknown + " onlyBad=" + onlyBad;
                    });

            test("IPC-18", "Per-step logging OFF: a 16-instruction run adds only 3 events (LOAD, HLT, RUN)", "3",
                    () -> {
                        c.setLogging(false);
                        logClient.flush(3000);
                        Thread.sleep(150);
                        long before = LogClient.queryStats(logSock).total();
                        c.load(demo);
                        c.run(1000, false);
                        logClient.flush(3000);
                        eventually(() -> LogClient.queryStats(logSock).total() - before >= 3, 3000);
                        Thread.sleep(150);                                   // would reveal any extra events
                        long after = LogClient.queryStats(logSock).total();
                        c.setLogging(true);
                        return String.valueOf(after - before);
                    });
        }

        test("IPC-19", "Logger missing: Core still executes correctly and counts dropped events", "CPU ok, dropped>0",
                () -> {
                    Path cs = tmp.resolve("core2.sock");
                    LogClient dead = new LogClient(tmp.resolve("nobody.sock"));
                    try (CoreServer s2 = new CoreServer(cs, dead)) {
                        s2.start();
                        try (CoreClient c2 = new CoreClient(cs, 1000)) {
                            c2.load(demo);
                            CpuState st = c2.run(1000, false).state();
                            dead.flush(3000);
                            boolean ok = st.ram[0x31] == 0x11 && st.ram[0x32] == 0x22 && st.halted;
                            return (ok ? "CPU ok" : "CPU WRONG") + ", dropped" + (dead.dropped() > 0 ? ">0" : "=0");
                        }
                    } finally { dead.close(); }
                });

        /* ---------- real OS processes ---------- */
        Path itLogs = tmp.resolve("it-logs");
        try (ProcessHarness h = ProcessHarness.start(itLogs, false)) {
            test("IT-01", "Logger, Core and this client are three different OS processes", "3 distinct PIDs",
                    () -> {
                        long a = ProcessHandle.current().pid(), b = h.coreProcess().pid(), d = h.loggerProcess().pid();
                        return (a != b && b != d && a != d && h.coreProcess().isAlive() && h.loggerProcess().isAlive())
                                ? "3 distinct PIDs" : "pids " + a + "," + b + "," + d;
                    });

            test("IT-02", "Full demo across real processes gives the Week 3 results", "ACC=22H R31=11H R32=22H q=[33H] HLT",
                    () -> {
                        try (CoreClient c = new CoreClient(h.coreSocket(), 3000)) {
                            c.load(demo);
                            CpuState s = c.run(1000, false).state();
                            return "ACC=" + hex(s.acc) + " R31=" + hex(s.ram[0x31]) + " R32=" + hex(s.ram[0x32])
                                    + " q=" + Arrays.stream(s.queue).mapToObj(IpcTests::hex).toList().toString().replace(", ", " ")
                                    + (s.halted ? " HLT" : " running");
                        }
                    });

            test("IT-03", "Logger process wrote the log file for the run", "ms51sim.log has LOAD + HLT lines",
                    () -> {
                        LogClient.queryStats(h.logSocket());
                        Thread.sleep(100);
                        List<String> lines = Files.readAllLines(itLogs.resolve("ms51sim.log"));
                        boolean ok = lines.stream().anyMatch(l -> l.contains("LOAD 26 bytes"))
                                && lines.stream().anyMatch(l -> l.contains("program terminated (HLT)"));
                        return ok ? "ms51sim.log has LOAD + HLT lines" : "lines=" + lines.size();
                    });

            test("IT-04", "Core process killed -> UI-side call fails with IOException (UI shows an error)", "IOException",
                    () -> {
                        try (CoreClient c = new CoreClient(h.coreSocket(), 3000)) {
                            c.ping();
                            h.coreProcess().destroyForcibly().waitFor();
                            try { c.step(); return "no error"; }
                            catch (java.io.IOException e) { return "IOException"; }
                        }
                    });
        }

        core.close();
        logClient.close();
        logServer.close();

        /* ---------- summary ---------- */
        long failed = rows.stream().filter(r -> !r.pass()).count();
        System.out.printf("%n%d tests, %d passed, %d failed%n", rows.size(), rows.size() - failed, failed);
        if (args.length > 0) writeMarkdown(Path.of(args[0]));
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void writeMarkdown(Path out) throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("| Test | Description | Expected result | Actual result | Status |\n");
        sb.append("|------|-------------|-----------------|---------------|--------|\n");
        for (Row r : rows) {
            sb.append("| ").append(r.id).append(" | ").append(r.what.replace("|", "\\|"))
              .append(" | ").append(r.expected.replace("|", "\\|"))
              .append(" | ").append(r.actual.replace("|", "\\|"))
              .append(" | ").append(r.pass ? "PASS" : "**FAIL**").append(" |\n");
        }
        Files.writeString(out, sb.toString());
    }
}
