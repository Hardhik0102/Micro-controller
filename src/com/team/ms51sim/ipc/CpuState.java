package com.team.ms51sim.ipc;

import com.team.ms51sim.CPU;
import com.team.ms51sim.Simulator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * A serialisable snapshot of everything the UI shows: registers, PSW, PC, SP,
 * the 256-byte internal RAM and the FIFO queue contents. The Core process
 * captures one after each request; the UI process applies it to a local mirror
 * {@link CPU} so the existing Week 3 rendering code works unchanged.
 */
public final class CpuState {
    public int acc, b, sp, psw, pc;
    public boolean halted;
    public boolean finished;          // halted, or PC ran past the loaded program
    public int programLength;
    public int[] ram = new int[256];
    public int[] queue = new int[0];  // front-to-back

    public static CpuState capture(Simulator sim) {
        CPU cpu = sim.cpu();
        CpuState s = new CpuState();
        s.acc = cpu.acc; s.b = cpu.b; s.sp = cpu.sp; s.psw = cpu.psw; s.pc = cpu.pc;
        s.halted = cpu.halted;
        s.finished = sim.finished();
        s.programLength = sim.programLength();
        System.arraycopy(cpu.ram, 0, s.ram, 0, 256);
        s.queue = cpu.queue.snapshot();
        return s;
    }

    /** Copy this snapshot into a (mirror) CPU. */
    public void applyTo(CPU cpu) {
        cpu.acc = acc; cpu.b = b; cpu.sp = sp; cpu.psw = psw; cpu.pc = pc;
        cpu.halted = halted;
        System.arraycopy(ram, 0, cpu.ram, 0, 256);
        cpu.queue.clear();
        for (int v : queue) cpu.queue.enqueue(v);
    }

    public void write(DataOutputStream o) throws IOException {
        o.writeByte(acc); o.writeByte(b); o.writeByte(sp); o.writeByte(psw);
        o.writeShort(pc);
        o.writeBoolean(halted);
        o.writeBoolean(finished);
        o.writeInt(programLength);
        for (int i = 0; i < 256; i++) o.writeByte(ram[i]);
        o.writeByte(queue.length);
        for (int v : queue) o.writeByte(v);
    }

    public static CpuState read(DataInputStream in) throws IOException {
        CpuState s = new CpuState();
        s.acc = in.readUnsignedByte(); s.b = in.readUnsignedByte();
        s.sp = in.readUnsignedByte();  s.psw = in.readUnsignedByte();
        s.pc = in.readUnsignedShort();
        s.halted = in.readBoolean();
        s.finished = in.readBoolean();
        s.programLength = in.readInt();
        for (int i = 0; i < 256; i++) s.ram[i] = in.readUnsignedByte();
        int n = in.readUnsignedByte();
        s.queue = new int[n];
        for (int i = 0; i < n; i++) s.queue[i] = in.readUnsignedByte();
        return s;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CpuState t)) return false;
        return acc == t.acc && b == t.b && sp == t.sp && psw == t.psw && pc == t.pc
                && halted == t.halted && finished == t.finished && programLength == t.programLength
                && Arrays.equals(ram, t.ram) && Arrays.equals(queue, t.queue);
    }

    @Override
    public int hashCode() { return Arrays.hashCode(ram) * 31 + pc; }

    @Override
    public String toString() {
        return String.format("CpuState[ACC=%02X B=%02X SP=%02X PSW=%02X PC=%04X halted=%b queue=%s]",
                acc, b, sp, psw, pc, halted, Arrays.toString(queue));
    }
}
