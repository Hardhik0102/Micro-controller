package com.team.ms51sim;

/**
 * CPU state for the Nuvoton MS51FB9AE (1T 8051 core).
 *
 * <p>This models only the parts of the processor that Week 2 requires:
 * the accumulator, the B register, the four register banks (R0-R7),
 * the Program Counter, the Stack Pointer, the Program Status Word (PSW)
 * with its flags, 256 bytes of internal RAM and a small SFR area.</p>
 *
 * <p>All values are kept masked to their real width (8-bit for registers,
 * 16-bit for the PC) so the simulator behaves like the real 8-bit device.</p>
 */
public class CPU {

    /* ---- SFR addresses (subset actually used by the simulator) ---- */
    public static final int SFR_SP  = 0x81;
    public static final int SFR_PSW = 0xD0;
    public static final int SFR_ACC = 0xE0;
    public static final int SFR_B   = 0xF0;

    /* ---- PSW bit positions ---- */
    public static final int PSW_CY = 7; // carry
    public static final int PSW_AC = 6; // auxiliary carry (bit 3 -> bit 4)
    public static final int PSW_F0 = 5; // user flag
    public static final int PSW_RS1 = 4; // register-bank select high
    public static final int PSW_RS0 = 3; // register-bank select low
    public static final int PSW_OV = 2; // overflow
    public static final int PSW_P  = 0; // parity of ACC (even parity)

    /** Internal data RAM: 0x00-0x7F direct/indirect, 0x80-0xFF indirect only on real HW. */
    public final int[] ram = new int[256];

    /** Program (code) memory. 8 KB is plenty for Week 2 demo programs. */
    public final int[] code = new int[0x2000];

    public int acc;   // accumulator (ACC / A)
    public int b;     // B register
    public int sp;    // stack pointer
    public int psw;   // program status word
    public int pc;    // program counter (16-bit)

    /** True once a program-termination instruction (HLT) has executed. */
    public boolean halted;

    public CPU() {
        reset();
    }

    /** Restore power-on / reset state (does NOT clear code memory). */
    public void reset() {
        for (int i = 0; i < ram.length; i++) ram[i] = 0;
        acc = 0;
        b = 0;
        psw = 0;
        pc = 0;
        sp = 0x07;      // 8051 reset value of SP
        halted = false;
    }

    /* ------------------------------------------------------------------ */
    /*  Register bank access                                             */
    /* ------------------------------------------------------------------ */

    /** Currently selected register bank (0-3) from PSW.RS1:RS0. */
    public int currentBank() {
        return (getFlag(PSW_RS1) ? 2 : 0) + (getFlag(PSW_RS0) ? 1 : 0);
    }

    /** RAM address that Rn currently maps to. */
    public int regAddress(int n) {
        return currentBank() * 8 + (n & 7);
    }

    public int getR(int n) {
        return ram[regAddress(n)] & 0xFF;
    }

    public void setR(int n, int value) {
        ram[regAddress(n)] = value & 0xFF;
    }

    /* ------------------------------------------------------------------ */
    /*  Direct address access (maps the SFR window onto the fields)      */
    /* ------------------------------------------------------------------ */

    public int readDirect(int addr) {
        addr &= 0xFF;
        switch (addr) {
            case SFR_ACC: return acc;
            case SFR_B:   return b;
            case SFR_SP:  return sp;
            case SFR_PSW: return psw;
            default:      return ram[addr] & 0xFF;
        }
    }

    public void writeDirect(int addr, int value) {
        addr &= 0xFF;
        value &= 0xFF;
        switch (addr) {
            case SFR_ACC: setAcc(value); break;
            case SFR_B:   b = value; break;
            case SFR_SP:  sp = value; break;
            case SFR_PSW: psw = value; break;
            default:      ram[addr] = value; break;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Accumulator                                                      */
    /* ------------------------------------------------------------------ */

    /** Write ACC and keep the parity flag consistent, exactly like the HW. */
    public void setAcc(int value) {
        acc = value & 0xFF;
        updateParity();
    }

    /** P = 1 when ACC has an odd number of set bits (8051 uses even parity). */
    public void updateParity() {
        setFlag(PSW_P, Integer.bitCount(acc & 0xFF) % 2 == 1);
    }

    /* ------------------------------------------------------------------ */
    /*  Flags                                                            */
    /* ------------------------------------------------------------------ */

    public boolean getFlag(int bit) {
        return ((psw >> bit) & 1) != 0;
    }

    public void setFlag(int bit, boolean set) {
        if (set) psw |= (1 << bit);
        else     psw &= ~(1 << bit) & 0xFF;
    }

    public boolean carry()    { return getFlag(PSW_CY); }
    public boolean aux()      { return getFlag(PSW_AC); }
    public boolean overflow() { return getFlag(PSW_OV); }
    public boolean parity()   { return getFlag(PSW_P); }
}
