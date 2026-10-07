package com.team.ms51sim.ipc;

import com.team.ms51sim.ExecContext;
import com.team.ms51sim.Instruction;
import com.team.ms51sim.Simulator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Binary encoding of {@link Simulator.StepResult} (what the UI needs for the trace pane). */
public final class StepCodec {
    private StepCodec() {}

    public static void write(DataOutputStream o, Simulator.StepResult r) throws IOException {
        o.writeInt(r.address);
        o.writeInt(r.opcode);
        o.writeInt(r.operand);
        o.writeInt(r.length);
        o.writeUTF(r.mnemonic);
        o.writeByte(r.category.ordinal());
        o.writeBoolean(r.fetched);
        o.writeBoolean(r.decoded);
        o.writeBoolean(r.executed);
        o.writeInt(r.pcBefore);
        o.writeInt(r.pcAfter);
        o.writeBoolean(r.halted);
        o.writeBoolean(r.invalid);
        o.writeBoolean(r.error != null);
        if (r.error != null) o.writeUTF(r.error);
        o.writeShort(r.changes.size());
        for (ExecContext.Change c : r.changes) {
            o.writeUTF(c.target);
            o.writeUTF(c.before);
            o.writeUTF(c.after);
        }
    }

    public static Simulator.StepResult read(DataInputStream in) throws IOException {
        Simulator.StepResult r = new Simulator.StepResult();
        r.address = in.readInt();
        r.opcode = in.readInt();
        r.operand = in.readInt();
        r.length = in.readInt();
        r.mnemonic = in.readUTF();
        r.category = Instruction.Category.values()[in.readUnsignedByte()];
        r.fetched = in.readBoolean();
        r.decoded = in.readBoolean();
        r.executed = in.readBoolean();
        r.pcBefore = in.readInt();
        r.pcAfter = in.readInt();
        r.halted = in.readBoolean();
        r.invalid = in.readBoolean();
        r.error = in.readBoolean() ? in.readUTF() : null;
        int n = in.readUnsignedShort();
        for (int i = 0; i < n; i++) {
            r.changes.add(new ExecContext.Change(in.readUTF(), in.readUTF(), in.readUTF()));
        }
        return r;
    }
}
