package com.team.ms51sim;

/**
 * Renders a {@link Simulator.StepResult} as the visible FETCH -> DECODE ->
 * EXECUTE trace described in the Week 2 brief.
 */
public final class TraceFormatter {

    private TraceFormatter() {}

    private static final String RULE = "------------------------------";

    public static String format(Simulator.StepResult r) {
        StringBuilder sb = new StringBuilder();

        if (r.error != null && !r.fetched) {
            return "-- " + r.error + " --\n";
        }

        sb.append(String.format("Instruction : %s%n", r.mnemonic));
        sb.append(String.format("Address     : %04XH   Opcode : %02XH   [%s]%n",
                r.address, r.opcode, r.category.label));
        sb.append(String.format("PC          : %04XH -> %04XH%n", r.pcBefore, r.pcAfter));
        sb.append("Execution Trace").append(System.lineSeparator());
        sb.append(RULE).append(System.lineSeparator());

        if (r.invalid) {
            sb.append("FETCH   [OK]").append(System.lineSeparator());
            sb.append("DECODE  [FAIL]  unknown opcode").append(System.lineSeparator());
            sb.append("EXECUTE [--]").append(System.lineSeparator());
            sb.append(RULE).append(System.lineSeparator());
            return sb.toString();
        }

        sb.append(mark("FETCH  ", r.fetched)).append(System.lineSeparator());
        sb.append(mark("DECODE ", r.decoded)).append(System.lineSeparator());
        sb.append(mark("EXECUTE", r.executed)).append(System.lineSeparator());
        sb.append("Result").append(System.lineSeparator());
        sb.append(RULE).append(System.lineSeparator());

        if (r.changes.isEmpty()) {
            sb.append("(no register / memory change)").append(System.lineSeparator());
        } else {
            for (ExecContext.Change c : r.changes) {
                if (c.before.isEmpty() && c.after.isEmpty()) {
                    sb.append("  ").append(c.target).append(System.lineSeparator());
                } else {
                    sb.append(String.format("  %-12s : %s -> %s%n", c.target, c.before, c.after));
                }
            }
        }
        if (r.halted) {
            sb.append(RULE).append(System.lineSeparator());
            sb.append(">>> PROGRAM TERMINATED <<<").append(System.lineSeparator());
        }
        sb.append(System.lineSeparator());
        return sb.toString();
    }

    private static String mark(String stage, boolean ok) {
        return stage + " " + (ok ? "[OK]" : "[FAIL]");
    }
}
