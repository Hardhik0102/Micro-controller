package com.team.ms51sim;

import com.team.ms51sim.bench.Benchmark;
import com.team.ms51sim.core.CoreProcess;
import com.team.ms51sim.launcher.Launcher;
import com.team.ms51sim.logging.LoggerProcess;
import com.team.ms51sim.ui.SimulatorUI;
import com.team.ms51sim.ui.UiProcess;

/**
 * Entry point.
 *
 * <pre>
 *   java -cp out com.team.ms51sim.Main                 Week 4: launch UI + Core + Logger as 3 processes
 *   java -cp out com.team.ms51sim.Main --standalone    Week 3 behaviour: everything in one process (baseline)
 *   java -cp out com.team.ms51sim.Main --cli           run the demo headless in-process and print the trace
 *   java -cp out com.team.ms51sim.Main --demo-ipc      headless 3-process demo (no GUI needed)
 *   java -cp out com.team.ms51sim.Main --bench         standalone vs multi-process benchmark
 *
 *   one role per terminal (what the launcher does for you):
 *   --logger   Logging process     --core   Core process     --ui   UI process
 * </pre>
 */
public class Main {

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "--multi";
        switch (mode) {
            case "--cli", "-c"   -> runHeadlessDemo();
            case "--standalone"  -> SimulatorUI.launch();
            case "--logger"      -> LoggerProcess.main(args);
            case "--core"        -> CoreProcess.main(args);
            case "--ui"          -> UiProcess.main(args);
            case "--demo-ipc"    -> Launcher.runIpcDemo();
            case "--bench"       -> Benchmark.main(java.util.Arrays.copyOfRange(args, 1, args.length));
            case "--multi"       -> Launcher.runWithUi();
            default -> {
                System.err.println("Unknown option " + mode + " (see the comment at the top of Main.java)");
                System.exit(2);
            }
        }
    }

    private static void runHeadlessDemo() {
        String demo = DemoPrograms.WEEK3_DEMO;

        Assembler.Program program = new Assembler().assemble(demo);
        Simulator sim = new Simulator();
        sim.load(program.code);

        System.out.println("=== week3-demo : headless run ===\n");
        for (Simulator.StepResult r : sim.run(1000)) {
            System.out.print(TraceFormatter.format(r));
        }
        CPU cpu = sim.cpu();
        System.out.printf("Final: ACC=%02XH  SP=%02XH  RAM[30H]=%02XH  RAM[31H]=%02XH  RAM[32H]=%02XH%n",
                cpu.acc, cpu.sp, cpu.ram[0x30], cpu.ram[0x31], cpu.ram[0x32]);
        int[] q = cpu.queue.snapshot();
        StringBuilder qs = new StringBuilder();
        for (int v : q) qs.append(String.format("%02XH ", v));
        System.out.printf("Queue: [ %s]  size %d%n", qs, cpu.queue.size());
    }
}
