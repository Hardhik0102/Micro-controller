package com.team.ms51sim;

import java.util.HashMap;
import java.util.Map;

import com.team.ms51sim.Instruction.Category;

/**
 * The instruction set implemented by the Week 2 prototype.
 *
 * <p>The brief requires <b>at least 8</b> processor-specific instructions
 * covering six functional areas. This prototype implements exactly that
 * minimum set for the Nuvoton MS51FB9AE (8051 core):</p>
 *
 * <pre>
 *   Data Transfer         MOV A,#data     MOV Rn,A
 *   Arithmetic            ADD A,Rn        SUBB A,#data
 *   Logical Operation     ANL A,#data
 *   Increment / Decrement INC A
 *   Control Flow          DJNZ Rn,rel
 *   Program Termination   HLT
 * </pre>
 *
 * <p>Opcodes are the real 8051 opcodes, except {@code HLT}: opcode {@code 0xA5}
 * is officially <b>undefined/reserved</b> on the 8051 core, so the project
 * reuses it as a synthetic program-termination instruction. This is documented
 * in {@code docs/week-02/instruction-set.md}.</p>
 *
 * <p>The lookup table is a {@link HashMap} keyed by opcode &ndash; this is the
 * data structure the DECODE stage uses.</p>
 */
public class InstructionSet {

    public static final int HLT_OPCODE = 0xA5;

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
    }
}
