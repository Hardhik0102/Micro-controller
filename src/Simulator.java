package com.team.ms51sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives one instruction through the logical FETCH -> DECODE -> EXECUTE
 * sequence and reports the result.
 *
 * <p>As the Week 2 brief requires, {@link #fetch()}, {@link #decode()} and
 * {@link #execute(ExecContext)} are three separate methods. They are
 * <em>logical</em> stages, not clock cycles &ndash; this is not a
 * cycle-accurate model.</p>
 */
public class Simulator {

    /** Everything the UI needs to display after one {@link #step()}. */
    public static final class StepResult {
        public int address;              // where the instruction was fetched from
        public int opcode;
        public int operand;              // second byte, if any
        public int length;
        public String mnemonic = "???";  // disassembled, operands resolved
        public Instruction.Category category = Instruction.Category.MISC;

        public boolean fetched;
        public boolean decoded;
        public boolean executed;

        public int pcBefore;
        public int pcAfter;
        public boolean halted;
        public boolean invalid;          // unknown opcode
        public String error;

        public final List<ExecContext.Change> changes = new ArrayList<>();
    }

    private final CPU cpu;
    private final InstructionSet iset;

    private int programLength;           // number of valid bytes loaded into code[]

    // transient decode state shared between fetch()/decode()/execute()
    private int fetchAddress;
    private int fetchedOpcode;
    private int fetchedOperand;
    private Instruction current;

    public Simulator() {
        this(new CPU(), new InstructionSet());
    }

    public Simulator(CPU cpu, InstructionSet iset) {
        this.cpu = cpu;
        this.iset = iset;
    }

    public CPU cpu() { return cpu; }
    public InstructionSet instructionSet() { return iset; }
    public int programLength() { return programLength; }
    public boolean finished() {
        return cpu.halted || cpu.pc >= programLength;
    }

    /* ------------------------------------------------------------------ */
    /*  Program loading                                                  */
    /* ------------------------------------------------------------------ */

    /** Load machine code at address 0 and reset the CPU. */
    public void load(int[] machineCode) {
        java.util.Arrays.fill(cpu.code, 0);
        for (int i = 0; i < machineCode.length && i < cpu.code.length; i++) {
            cpu.code[i] = machineCode[i] & 0xFF;
        }
        programLength = Math.min(machineCode.length, cpu.code.length);
        cpu.reset();
    }

    public void reset() {
        cpu.reset();
    }

    /* ------------------------------------------------------------------ */
    /*  FETCH / DECODE / EXECUTE                                         */
    /* ------------------------------------------------------------------ */

    /**
     * FETCH: read the opcode (and operand byte for 2-byte instructions) that
     * the Program Counter points at, then advance the PC past the instruction.
     */
    private void fetch() {
        fetchAddress = cpu.pc;
        fetchedOpcode = cpu.code[cpu.pc & 0x1FFF] & 0xFF;
        current = iset.get(fetchedOpcode);

        int len = (current != null) ? current.length : 1;
        fetchedOperand = (len == 2) ? (cpu.code[(cpu.pc + 1) & 0x1FFF] & 0xFF) : 0;
        cpu.pc = (cpu.pc + len) & 0xFFFF;   // PC now points at the next instruction
    }

    /**
     * DECODE: identify the opcode, its operation and its operands. Here this is
     * a single HashMap lookup plus building a readable, operand-resolved
     * mnemonic for the trace.
     */
    private String decode() {
        if (current == null) return null;
        String m = current.mnemonic;
        m = m.replace("#data", String.format("#%02XH", fetchedOperand));
        m = m.replace("direct", String.format("%02XH", fetchedOperand));
        if (m.contains("rel")) {
            int target = (cpu.pc + (byte) fetchedOperand) & 0xFFFF;
            m = m.replace("rel", String.format("%04XH", target));
        }
        return m;
    }

    /** EXECUTE: perform the operation, updating registers, memory and flags. */
    private void execute(ExecContext ctx) {
        current.operation.run(cpu, fetchedOperand, ctx);
    }

    /** update_Status(): keep derived status (parity) consistent after execute. */
    private void updateStatus() {
        cpu.updateParity();
    }

    /* ------------------------------------------------------------------ */
    /*  Public stepping                                                  */
    /* ------------------------------------------------------------------ */

    /** Process exactly one instruction and return what happened. */
    public StepResult step() {
        StepResult r = new StepResult();
        r.pcBefore = cpu.pc;

        if (finished()) {
            r.halted = cpu.halted;
            r.error = cpu.halted ? "CPU is halted" : "PC past end of program";
            r.pcAfter = cpu.pc;
            return r;
        }

        // FETCH
        fetch();
        r.address = fetchAddress;
        r.opcode = fetchedOpcode;
        r.fetched = true;

        if (current == null) {
            r.invalid = true;
            r.error = String.format("Unknown opcode %02XH at %04XH", fetchedOpcode, fetchAddress);
            r.pcAfter = cpu.pc;
            return r;
        }

        r.length = current.length;
        r.operand = fetchedOperand;
        r.category = current.category;

        // DECODE
        r.mnemonic = decode();
        r.decoded = true;

        // EXECUTE
        ExecContext ctx = new ExecContext();
        execute(ctx);
        updateStatus();
        r.executed = true;
        r.changes.addAll(ctx.changes());

        r.pcAfter = cpu.pc;
        r.halted = cpu.halted;
        return r;
    }

    /**
     * RUN: step repeatedly until the program terminates (HLT), the PC runs off
     * the end of the loaded program, an illegal opcode is hit, or the guard
     * limit is reached (protects against runaway loops).
     */
    public List<StepResult> run(int maxSteps) {
        List<StepResult> trace = new ArrayList<>();
        int guard = 0;
        while (!finished() && guard++ < maxSteps) {
            StepResult r = step();
            trace.add(r);
            if (r.invalid) break;
        }
        return trace;
    }
}
