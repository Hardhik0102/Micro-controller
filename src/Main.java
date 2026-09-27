package com.team.ms51sim;

import com.team.ms51sim.ui.SimulatorUI;

/**
 * Entry point for the Week 2 prototype.
 *
 * <pre>
 *   java -cp out com.team.ms51sim.Main          launch the Swing UI
 *   java -cp out com.team.ms51sim.Main --cli    run demo1 headless and print the trace
 * </pre>
 */
public class Main {

    public static void main(String[] args) {
        if (args.length > 0 && (args[0].equals("--cli") || args[0].equals("-c"))) {
            runHeadlessDemo();
        } else {
            SimulatorUI.launch();
        }
    }

    private static void runHeadlessDemo() {
        String demo =
                "        MOV  A,#7EH\n" +
                "        MOV  30H,A\n" +
                "        PUSH 30H\n" +
                "        MOV  A,#00H\n" +
                "        POP  A\n" +
                "        MOV  A,#11H\n" +
                "        ENQ  A\n" +
                "        MOV  A,#22H\n" +
                "        ENQ  A\n" +
                "        MOV  A,#33H\n" +
                "        ENQ  A\n" +
                "        DEQ  A\n" +
                "        MOV  31H,A\n" +
                "        DEQ  A\n" +
                "        MOV  32H,A\n" +
                "        HLT\n";

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
