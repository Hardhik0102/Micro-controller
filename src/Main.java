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
                "        MOV  A,#3\n" +
                "        MOV  R2,A\n" +
                "        MOV  A,#5\n" +
                "        MOV  R1,A\n" +
                "        MOV  A,#0\n" +
                "loop:   ADD  A,R2\n" +
                "        DJNZ R1,loop\n" +
                "        SUBB A,#1\n" +
                "        ANL  A,#0CH\n" +
                "        INC  A\n" +
                "        HLT\n";

        Assembler.Program program = new Assembler().assemble(demo);
        Simulator sim = new Simulator();
        sim.load(program.code);

        System.out.println("=== demo1 : headless run ===\n");
        for (Simulator.StepResult r : sim.run(1000)) {
            System.out.print(TraceFormatter.format(r));
        }
        CPU cpu = sim.cpu();
        System.out.printf("Final: ACC=%02XH  R1=%02XH  R2=%02XH  PSW=%02XH%n",
                cpu.acc, cpu.getR(1), cpu.getR(2), cpu.psw);
    }
}
