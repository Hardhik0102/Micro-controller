package com.team.ms51sim;

/**
 * 8-bit arithmetic/logic helpers that update the PSW flags exactly the way the
 * 8051 core does. Kept separate from {@link CPU} so the flag rules can be unit
 * tested on their own.
 */
public final class Alu {

    private Alu() {}

    /**
     * ACC = ACC + operand, updating CY, AC, OV and P (ADD, not ADDC).
     * Records every flag change into {@code ctx}.
     */
    public static void add(CPU cpu, int operand, ExecContext ctx) {
        int a = cpu.acc & 0xFF;
        int m = operand & 0xFF;

        boolean cyOld = cpu.carry(), acOld = cpu.aux(), ovOld = cpu.overflow(), pOld = cpu.parity();

        int result = a + m;
        boolean cy = result > 0xFF;
        boolean ac = ((a & 0x0F) + (m & 0x0F)) > 0x0F;
        boolean c6 = ((a & 0x7F) + (m & 0x7F)) > 0x7F;   // carry into bit 7
        boolean ov = c6 ^ cy;                             // carry-in XOR carry-out of bit 7

        int accBefore = cpu.acc;
        cpu.setAcc(result & 0xFF);
        ctx.recordByte("ACC", accBefore, cpu.acc);

        cpu.setFlag(CPU.PSW_CY, cy);
        cpu.setFlag(CPU.PSW_AC, ac);
        cpu.setFlag(CPU.PSW_OV, ov);
        ctx.recordFlag("CY", cyOld, cy);
        ctx.recordFlag("AC", acOld, ac);
        ctx.recordFlag("OV", ovOld, ov);
        ctx.recordFlag("P",  pOld, cpu.parity());
    }

    /**
     * ACC = ACC - operand - CY (SUBB). Updates CY(borrow), AC, OV and P.
     */
    public static void subb(CPU cpu, int operand, ExecContext ctx) {
        int a = cpu.acc & 0xFF;
        int m = operand & 0xFF;
        int borrowIn = cpu.carry() ? 1 : 0;

        boolean cyOld = cpu.carry(), acOld = cpu.aux(), ovOld = cpu.overflow(), pOld = cpu.parity();

        int result = a - m - borrowIn;
        boolean cy = result < 0;                                         // borrow out
        boolean ac = ((a & 0x0F) - (m & 0x0F) - borrowIn) < 0;
        boolean b6 = ((a & 0x7F) - (m & 0x7F) - borrowIn) < 0;           // borrow into bit 7
        boolean ov = b6 ^ cy;

        int accBefore = cpu.acc;
        cpu.setAcc(result & 0xFF);
        ctx.recordByte("ACC", accBefore, cpu.acc);

        cpu.setFlag(CPU.PSW_CY, cy);
        cpu.setFlag(CPU.PSW_AC, ac);
        cpu.setFlag(CPU.PSW_OV, ov);
        ctx.recordFlag("CY", cyOld, cy);
        ctx.recordFlag("AC", acOld, ac);
        ctx.recordFlag("OV", ovOld, ov);
        ctx.recordFlag("P",  pOld, cpu.parity());
    }

    /** Bitwise op on ACC (AND/OR/XOR). Only the parity flag is affected. */
    public static void logic(CPU cpu, int value, ExecContext ctx) {
        boolean pOld = cpu.parity();
        int accBefore = cpu.acc;
        cpu.setAcc(value & 0xFF);
        ctx.recordByte("ACC", accBefore, cpu.acc);
        ctx.recordFlag("P", pOld, cpu.parity());
    }
}
