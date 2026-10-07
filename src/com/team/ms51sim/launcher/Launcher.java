package com.team.ms51sim.launcher;

import com.team.ms51sim.Assembler;
import com.team.ms51sim.DemoPrograms;
import com.team.ms51sim.Simulator;
import com.team.ms51sim.TraceFormatter;
import com.team.ms51sim.ipc.CoreClient;
import com.team.ms51sim.ipc.CoreReply;
import com.team.ms51sim.logging.LogClient;
import com.team.ms51sim.logging.LogStats;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Top-level integration: <b>UI process &rarr; Core process &rarr; Logger process</b>.
 */
public final class Launcher {

    private Launcher() {}

    private static Path logDir() {
        return Paths.get(System.getProperty("ms51.logdir", "logs"));
    }

    /** Start Logger + Core as child processes, then the Swing UI; stop everything when the UI closes. */
    public static void runWithUi() throws Exception {
        try (ProcessHarness h = ProcessHarness.start(logDir(), true)) {
            System.out.println("[launcher] logger pid " + h.loggerProcess().pid()
                    + ", core pid " + h.coreProcess().pid() + ", launcher pid " + ProcessHandle.current().pid());
            Process ui = h.spawn("--ui");
            System.out.println("[launcher] ui pid " + ui.pid() + "  (close the window to stop all processes)");
            ui.waitFor();
        }
        System.out.println("[launcher] all processes stopped. Logs: " + logDir().toAbsolutePath());
    }

    /**
     * Headless end-to-end demonstration (no GUI needed): a command-line "UI"
     * drives the Core over IPC, and the Core logs to the Logger process.
     */
    public static void runIpcDemo() throws Exception {
        try (ProcessHarness h = ProcessHarness.start(logDir(), false)) {
            System.out.println("=== Week 4 demo: UI -> Core -> CPU/Memory/Stack/Queue -> Logger ===");
            System.out.println("Processes: launcher(UI role) pid " + ProcessHandle.current().pid()
                    + " | core pid " + h.coreProcess().pid() + " | logger pid " + h.loggerProcess().pid() + "\n");

            Assembler.Program program = new Assembler().assemble(DemoPrograms.WEEK3_DEMO);
            try (CoreClient core = new CoreClient(h.coreSocket(), 3000)) {
                core.load(program.code);
                CoreReply last = null;
                while (true) {
                    last = core.step();
                    if (last.last() != null) System.out.print(TraceFormatter.format(last.last()));
                    if (last.state().finished) break;
                }
                var s = last.state();
                System.out.printf("Final: ACC=%02XH SP=%02XH RAM[30H]=%02XH RAM[31H]=%02XH RAM[32H]=%02XH%n",
                        s.acc, s.sp, s.ram[0x30], s.ram[0x31], s.ram[0x32]);
                StringBuilder q = new StringBuilder();
                for (int v : s.queue) q.append(String.format("%02XH ", v));
                System.out.printf("Queue: [ %s] size %d%n", q, s.queue.length);

                // an error travelling UI -> Core -> Logger
                core.log(3, "demo: simulated UI-side error");
                core.shutdownCore();
            }
            Thread.sleep(300);
            LogStats st = LogClient.queryStats(h.logSocket());
            System.out.println("\nLogger counters: total=" + st.total() + " info=" + st.info()
                    + " warn=" + st.warn() + " error=" + st.error());
            Path lf = logDir().resolve("ms51sim.log");
            System.out.println("Log file: " + lf.toAbsolutePath() + "  (" + Files.readAllLines(lf).size() + " lines)");
        }
    }
}
