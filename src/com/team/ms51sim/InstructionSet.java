package com.team.ms51sim;

import java.util.HashMap;
import java.util.Map;

import com.team.ms51sim.Instruction.Category;

/**
 * The instruction set implemented by the simulator.
 *
 * <p><b>Week 2</b> - the 8 required instructions over the six functional areas:</p>
 * <pre>
 *   Data Transfer         MOV A,#data     MOV Rn,A
 *   Arithmetic            ADD A,Rn        SUBB A,#data
 *   Logical Operation     ANL A,#data
 *   Increment / Decrement INC A
 *   Control Flow          DJNZ Rn,rel
 *   Program Termination   HLT
 * </pre>
 *
 * <p><b>Week 3</b> - memory, stack and FIFO-queue instructions:</p>
 * <pre>
 *   Memory   MOV A,direct   MOV direct,A          (read / write internal RAM)
 *   Stack    PUSH direct     POP direct           (real 8051 opcodes C0H / D0H)
 *   Queue    ENQ A           DEQ A                (project-specific, opcodes A6H / A7H)
 * </pre>
 *
 * <p>Opcodes are the real 8051 opcodes, except three synthetic ones the project
 * defines because the 8051 core has no equivalent: {@code HLT} = {@code A5H}
 * (the one truly unused 8051 opcode), {@code ENQ} = {@code A6H} and
 * {@code DEQ} = {@code A7H}. All are documented in
 * {@code docs/week-03/instruction-set-week3.md}.</p>
 *
 * <p>The lookup table is a {@link HashMap} keyed by opcode &ndash; this is the
 * data structure the DECODE stage uses.</p>
 */
public class InstructionSet {

    public static final int HLT_OPCODE = 0xA5;
    public static final int ENQ_OPCODE = 0xA6;
    public static final int DEQ_OPCODE = 0xA7;

    private final Map<Integer, Instruction> table = new HashMap<>();

    public InstructionSet() {
        build();
    }

    public Instruction get(int opcode) {
        return table.get(opcode & 0xFF);
    }

    public boolean has(int opcode) {
        return table.containsKey(opcode & 0xFF);
    }

    public Map<Integer, Instruction> table() {
        return table;
    }

    /* ------------------------------------------------------------------ */

    private void put(Instruction i) {
        table.put(i.opcode, i);
    }

    private void build() {

        /* ============ Data Transfer (2) ============ */

        // 1. MOV A,#data          opcode 74H, 2 bytes
        put(new Instruction(0x74, "MOV  A,#data", Category.DATA_TRANSFER, 2,
                (cpu, op, ctx) -> {
                    int before = cpu.acc;
                    cpu.setAcc(op);
                    ctx.recordByte("ACC", before, cpu.acc);
                }));

        // 2. MOV Rn,A             opcode F8H-FFH, 1 byte
        for (int n = 0; n < 8; n++) {
            final int reg = n;
            put(new Instruction(0xF8 + n, "MOV  R" + n + ",A", Category.DATA_TRANSFER, 1,
                    (cpu, op, ctx) -> {
                        int before = cpu.getR(reg);
                        cpu.setR(reg, cpu.acc);
                        ctx.recordByte("R" + reg, before, cpu.getR(reg));
                    }));
        }

        /* ============ Arithmetic (2) ============ */

        // 3. ADD A,Rn             opcode 28H-2FH, 1 byte
        for (int n = 0; n < 8; n++) {
            final int reg = n;
            put(new Instruction(0x28 + n, "ADD  A,R" + n, Category.ARITHMETIC, 1,
                    (cpu, op, ctx) -> Alu.add(cpu, cpu.getR(reg), ctx)));
        }

        // 4. SUBB A,#data         opcode 94H, 2 bytes  (subtract with borrow)
        put(new Instruction(0x94, "SUBB A,#data", Category.ARITHMETIC, 2,
                (cpu, op, ctx) -> Alu.subb(cpu, op, ctx)));

        /* ============ Logical Operation (1) ============ */

        // 5. ANL A,#data          opcode 54H, 2 bytes
        put(new Instruction(0x54, "ANL  A,#data", Category.LOGICAL, 2,
                (cpu, op, ctx) -> Alu.logic(cpu, cpu.acc & op, ctx)));

        /* ============ Increment / Decrement (1) ============ */

        // 6. INC A                opcode 04H, 1 byte
        put(new Instruction(0x04, "INC  A", Category.INC_DEC, 1,
                (cpu, op, ctx) -> {
                    int before = cpu.acc;
                    cpu.setAcc(cpu.acc + 1);
                    ctx.recordByte("ACC", before, cpu.acc);
                }));

        /* ============ Control Flow (1) ============ */

        // 7. DJNZ Rn,rel          opcode D8H-DFH, 2 bytes
        for (int n = 0; n < 8; n++) {
            final int reg = n;
            put(new Instruction(0xD8 + n, "DJNZ R" + n + ",rel", Category.CONTROL_FLOW, 2,
                    (cpu, op, ctx) -> {
                        int rBefore = cpu.getR(reg);
                        cpu.setR(reg, cpu.getR(reg) - 1);
                        ctx.recordByte("R" + reg, rBefore, cpu.getR(reg));
                        if (cpu.getR(reg) != 0) {
                            int pcBefore = cpu.pc;
                            cpu.pc = (cpu.pc + (byte) op) & 0xFFFF;
                            ctx.pcOverridden = true;
                            ctx.recordWord("PC", pcBefore, cpu.pc);
                            ctx.note("branch taken");
                        } else {
                            ctx.note("branch not taken (R" + reg + " = 0)");
                        }
                    }));
        }

        /* ============ Program Termination (1) ============ */

        // 8. HLT                  opcode A5H, 1 byte  (project-specific)
        put(new Instruction(HLT_OPCODE, "HLT", Category.PROGRAM_TERMINATION, 1,
                (cpu, op, ctx) -> {
                    cpu.halted = true;
                    ctx.note("program terminated (HLT)");
                }));

        buildWeek3();
    }

    /** Week 3: memory read/write, stack PUSH/POP, and FIFO queue ENQ/DEQ. */
    private void buildWeek3() {

        /* ============ Memory (read / write internal RAM) ============ */

        // MOV A,direct           opcode E5H, 2 bytes  - read RAM[addr] into ACC
        put(new Instruction(0xE5, "MOV  A,direct", Category.MEMORY, 2,
                (cpu, op, ctx) -> {
                    int addr = op & 0xFF;
                    int before = cpu.acc;
                    cpu.setAcc(cpu.readDirect(addr));
                    ctx.recordByte("ACC", before, cpu.acc);
                    ctx.note("read " + CPU.sfrName(addr));
                }));

        // MOV direct,A           opcode F5H, 2 bytes  - write ACC to RAM[addr]
        put(new Instruction(0xF5, "MOV  direct,A", Category.MEMORY, 2,
                (cpu, op, ctx) -> {
                    int addr = op & 0xFF;
                    int before = cpu.readDirect(addr);
                    cpu.writeDirect(addr, cpu.acc);
                    ctx.recordByte("[" + CPU.sfrName(addr) + "]", before, cpu.acc);
                    ctx.note("write " + CPU.sfrName(addr));
                }));

        /* ============ Stack (SP + PUSH / POP) ============ */

        // PUSH direct            opcode C0H, 2 bytes
        put(new Instruction(0xC0, "PUSH direct", Category.STACK, 2,
                (cpu, op, ctx) -> {
                    int addr = op & 0xFF;
                    int val = cpu.readDirect(addr);
                    int spOld = cpu.sp;
                    int slot = cpu.push(val);
                    ctx.recordByte("SP", spOld, cpu.sp);
                    ctx.recordByte("RAM[" + String.format("%02XH", slot) + "]", 0, val);
                    ctx.note("PUSH " + CPU.sfrName(addr) + " (" + ExecContext.hex2(val) + ")");
                }));

        // POP direct             opcode D0H, 2 bytes
        put(new Instruction(0xD0, "POP  direct", Category.STACK, 2,
                (cpu, op, ctx) -> {
                    int addr = op & 0xFF;
                    int spOld = cpu.sp;
                    int before = cpu.readDirect(addr);
                    int val = cpu.pop();
                    cpu.writeDirect(addr, val);
                    ctx.recordByte(CPU.sfrName(addr), before, val);
                    ctx.recordByte("SP", spOld, cpu.sp);
                    ctx.note("POP -> " + CPU.sfrName(addr) + " (" + ExecContext.hex2(val) + ")");
                }));

        /* ============ FIFO Queue (ENQ / DEQ) ============ */

        // ENQ A                  opcode A6H, 1 byte  (project-specific)
        put(new Instruction(ENQ_OPCODE, "ENQ  A", Category.QUEUE, 1,
                (cpu, op, ctx) -> {
                    boolean cyOld = cpu.carry();
                    boolean ok = cpu.queue.enqueue(cpu.acc);
                    cpu.setFlag(CPU.PSW_CY, !ok);
                    ctx.recordFlag("CY", cyOld, !ok);
                    if (ok) {
                        ctx.note("ENQUEUE " + ExecContext.hex2(cpu.acc)
                                + "   (queue size " + cpu.queue.size() + "/" + cpu.queue.capacity() + ")");
                    } else {
                        ctx.note("ENQUEUE " + ExecContext.hex2(cpu.acc)
                                + " rejected - queue FULL  (CY = 1)");
                    }
                }));

        // DEQ A                  opcode A7H, 1 byte  (project-specific)
        put(new Instruction(DEQ_OPCODE, "DEQ  A", Category.QUEUE, 1,
                (cpu, op, ctx) -> {
                    boolean cyOld = cpu.carry();
                    int accBefore = cpu.acc;
                    int v = cpu.queue.dequeue();
                    if (v < 0) {
                        cpu.setFlag(CPU.PSW_CY, true);
                        ctx.recordFlag("CY", cyOld, true);
                        ctx.note("DEQUEUE rejected - queue EMPTY  (CY = 1)");
                    } else {
                        cpu.setAcc(v);
                        cpu.setFlag(CPU.PSW_CY, false);
                        ctx.recordByte("ACC", accBefore, cpu.acc);
                        ctx.recordFlag("CY", cyOld, false);
                        ctx.note("DEQUEUE " + ExecContext.hex2(v)
                                + "   (queue size " + cpu.queue.size() + "/" + cpu.queue.capacity() + ")");
                    }
                }));
    }
}
