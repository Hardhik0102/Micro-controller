package com.team.ms51sim;

/** Assembly programs shared by the CLI demo, the IPC demo, the tests and the benchmark. */
public final class DemoPrograms {
    private DemoPrograms() {}

    /** Week 3 demo: memory write, stack PUSH/POP, FIFO ENQ/DEQ. Ends with HLT. */
    public static final String WEEK3_DEMO =
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

    /**
     * Busy loop used to generate a long instruction stream for benchmarking:
     * {@code 3 + 513 * outer} instructions. outer=40 gives about 20 500.
     */
    public static String benchmarkLoop(int outer) {
        return  "        MOV  A,#" + String.format("%02X", outer & 0xFF) + "H\n" +
                "        MOV  R2,A\n" +
                "OUTER:  MOV  A,#0FFH\n" +
                "        MOV  R1,A\n" +
                "INNER:  INC  A\n" +
                "        DJNZ R1,INNER\n" +
                "        DJNZ R2,OUTER\n" +
                "        HLT\n";
    }
}
