package com.team.ms51sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the human-readable "what changed" notes produced while an
 * instruction executes. The UI prints these under the execution trace so the
 * user can see the CPU state change caused by each Step.
 */
public class ExecContext {

    /** A single "NAME : old -> new" style change record. */
    public static final class Change {
        public final String target;   // "R1", "PC", "Memory[20]", "CY" ...
        public final String before;
        public final String after;

        public Change(String target, String before, String after) {
            this.target = target;
            this.before = before;
            this.after = after;
        }

        @Override
        public String toString() {
            return String.format("%-12s : %s -> %s", target, before, after);
        }
    }

    private final List<Change> changes = new ArrayList<>();

    /** True when the instruction redirected control flow itself (jump taken). */
    public boolean pcOverridden;

    public void recordByte(String target, int before, int after) {
        changes.add(new Change(target, hex2(before), hex2(after)));
    }

    public void recordWord(String target, int before, int after) {
        changes.add(new Change(target, hex4(before), hex4(after)));
    }

    public void recordFlag(String name, boolean before, boolean after) {
        if (before != after) {
            changes.add(new Change(name, before ? "1" : "0", after ? "1" : "0"));
        }
    }

    public void note(String text) {
        changes.add(new Change(text, "", ""));
    }

    public List<Change> changes() {
        return changes;
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    public static String hex2(int v) {
        return String.format("%02XH", v & 0xFF);
    }

    public static String hex4(int v) {
        return String.format("%04XH", v & 0xFFFF);
    }
}
