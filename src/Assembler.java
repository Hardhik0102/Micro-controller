package com.team.ms51sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A small two-pass assembler for the instructions the simulator implements.
 * It turns readable source (see {@code programs/*.asm}) into machine code that
 * {@link Simulator#load(int[])} can run, and produces a listing the UI shows in
 * the program pane.
 *
 * <p>Supported syntax:</p>
 * <pre>
 *   ; comment
 *   label:                 (own line or before an instruction)
 *   MOV  A,#25             decimal immediate
 *   MOV  A,#0FH            hex immediate (0x.. or ..H)
 *   MOV  R2,A
 *   MOV  A,30H             read internal RAM     (Week 3)
 *   MOV  30H,A             write internal RAM    (Week 3)
 *   ADD  A,R2
 *   SUBB A,#1
 *   ANL  A,#0CH
 *   INC  A
 *   DJNZ R1,loop           jump target is a label
 *   PUSH A   / PUSH 30H    stack push            (Week 3)
 *   POP  A   / POP  30H    stack pop             (Week 3)
 *   ENQ  A                 enqueue ACC into FIFO (Week 3)
 *   DEQ  A                 dequeue FIFO into ACC (Week 3)
 *   HLT
 * </pre>
 *
 * <p>A "direct" operand is a number ({@code 30H}, {@code 0x30}, {@code 48}) or
 * one of the names {@code A}/{@code ACC}, {@code B}, {@code PSW}, {@code SP}.</p>
 */
public class Assembler {

    /** One assembled source line, for display in the UI. */
    public static final class Line {
        public final int address;
        public final String source;
        public final int[] bytes;

        public Line(int address, String source, int[] bytes) {
            this.address = address;
            this.source = source;
            this.bytes = bytes;
        }

        public String bytesHex() {
            StringBuilder sb = new StringBuilder();
            for (int bt : bytes) sb.append(String.format("%02X ", bt));
            return sb.toString().trim();
        }
    }

    public static final class Program {
        public final int[] code;
        public final List<Line> listing;

        public Program(int[] code, List<Line> listing) {
            this.code = code;
            this.listing = listing;
        }
    }

    public static class AssemblyException extends RuntimeException {
        public AssemblyException(String msg) { super(msg); }
    }

    public Program assemble(String source) {
        List<String[]> parsed = new ArrayList<>();   // [mnemonic, operandString]
        Map<String, Integer> labels = new HashMap<>();

        /* ---------- pass 1: addresses + labels ---------- */
        int address = 0;
        for (String raw : source.split("\\r?\\n")) {
            String line = stripComment(raw).trim();
            if (line.isEmpty()) continue;

            // leading label(s)
            while (true) {
                int colon = line.indexOf(':');
                if (colon < 0) break;
                String maybe = line.substring(0, colon).trim();
                if (maybe.isEmpty() || maybe.contains(" ")) break;
                labels.put(maybe.toUpperCase(Locale.ROOT), address);
                line = line.substring(colon + 1).trim();
                if (line.isEmpty()) break;
            }
            if (line.isEmpty()) continue;

            String[] mo = splitMnemonic(line);
            parsed.add(mo);
            address += encodedLength(mo[0], mo[1]);
        }

        /* ---------- pass 2: emit bytes ---------- */
        List<Integer> out = new ArrayList<>();
        List<Line> listing = new ArrayList<>();
        address = 0;
        for (String[] mo : parsed) {
            int[] bytes = encode(mo[0], mo[1], address, labels);
            listing.add(new Line(address, formatSource(mo), bytes));
            for (int bt : bytes) out.add(bt & 0xFF);
            address += bytes.length;
        }

        int[] code = new int[out.size()];
        for (int i = 0; i < code.length; i++) code[i] = out.get(i);
        return new Program(code, listing);
    }

    /* ------------------------------------------------------------------ */

    private static String stripComment(String s) {
        int i = s.indexOf(';');
        return (i >= 0) ? s.substring(0, i) : s;
    }

    private static String formatSource(String[] mo) {
        return mo[1].isEmpty() ? mo[0] : mo[0] + "  " + mo[1];
    }

    /** Split "MOV A,#10" into {"MOV", "A,#10"}. */
    private static String[] splitMnemonic(String line) {
        line = line.trim().replaceAll("\\s+", " ");
        int sp = line.indexOf(' ');
        if (sp < 0) return new String[]{line.toUpperCase(Locale.ROOT), ""};
        return new String[]{
                line.substring(0, sp).toUpperCase(Locale.ROOT),
                line.substring(sp + 1).replace(" ", "").toUpperCase(Locale.ROOT)
        };
    }

    private int encodedLength(String mn, String ops) {
        String[] p = ops.isEmpty() ? new String[0] : ops.split(",");
        switch (mn) {
            case "INC": case "HLT":
                return 1;
            case "ENQ": case "DEQ":
                return 1;                            // ENQ A ; DEQ A
            case "MOV":
                // MOV Rn,A is 1 byte; every other MOV form (A,#data / A,direct / direct,A) is 2
                return (p.length == 2 && p[1].equals("A") && p[0].matches("R[0-7]")) ? 1 : 2;
            case "ADD":
                return 1;                            // ADD A,Rn
            case "SUBB": case "ANL":
                return 2;                            // SUBB A,#data ; ANL A,#data
            case "DJNZ":
                return 2;                            // DJNZ Rn,rel
            case "PUSH": case "POP":
                return 2;                            // PUSH direct ; POP direct
            default:
                throw new AssemblyException("Unknown / unsupported mnemonic: " + mn);
        }
    }

    private int[] encode(String mn, String ops, int address, Map<String, Integer> labels) {
        String[] p = ops.isEmpty() ? new String[0] : ops.split(",");
        switch (mn) {
            case "HLT":
                return new int[]{InstructionSet.HLT_OPCODE};

            case "INC":
                require(ops.equals("A"), "INC expects A  (only 'INC A' is supported)");
                return new int[]{0x04};

            case "MOV": {
                require(p.length == 2, "MOV expects two operands");
                String d = p[0], s = p[1];
                if (d.equals("A") && s.startsWith("#"))    return new int[]{0x74, imm(s)};
                if (d.matches("R[0-7]") && s.equals("A"))  return new int[]{0xF8 + reg(d)};
                if (d.equals("A"))                         return new int[]{0xE5, direct(s)};   // MOV A,direct
                if (s.equals("A"))                         return new int[]{0xF5, direct(d)};   // MOV direct,A
                throw new AssemblyException("Unsupported MOV form: " + ops
                        + "  (supported: MOV A,#data | MOV Rn,A | MOV A,direct | MOV direct,A)");
            }

            case "PUSH":
                require(p.length == 1, "PUSH expects one operand (PUSH A or PUSH <addr>)");
                return new int[]{0xC0, direct(p[0])};

            case "POP":
                require(p.length == 1, "POP expects one operand (POP A or POP <addr>)");
                return new int[]{0xD0, direct(p[0])};

            case "ENQ":
                require(ops.equals("A"), "ENQ expects A  (only 'ENQ A' is supported)");
                return new int[]{InstructionSet.ENQ_OPCODE};

            case "DEQ":
                require(ops.equals("A"), "DEQ expects A  (only 'DEQ A' is supported)");
                return new int[]{InstructionSet.DEQ_OPCODE};

            case "ADD":
                require(p.length == 2 && p[0].equals("A") && p[1].matches("R[0-7]"),
                        "ADD expects A,Rn");
                return new int[]{0x28 + reg(p[1])};

            case "SUBB":
                require(p.length == 2 && p[0].equals("A") && p[1].startsWith("#"),
                        "SUBB expects A,#data");
                return new int[]{0x94, imm(p[1])};

            case "ANL":
                require(p.length == 2 && p[0].equals("A") && p[1].startsWith("#"),
                        "ANL expects A,#data");
                return new int[]{0x54, imm(p[1])};

            case "DJNZ": {
                require(p.length == 2 && p[0].matches("R[0-7]"), "DJNZ expects Rn,<label>");
                int target = resolve(p[1], labels);
                int rel = target - (address + 2);
                return new int[]{0xD8 + reg(p[0]), checkRel(rel, p[1])};
            }

            default:
                throw new AssemblyException("Unknown / unsupported mnemonic: " + mn);
        }
    }

    private static void require(boolean cond, String msg) {
        if (!cond) throw new AssemblyException(msg);
    }

    private static int reg(String r) {
        return r.charAt(1) - '0';
    }

    private static int imm(String s) {
        return parseNumber(s.substring(1)) & 0xFF;   // drop leading '#'
    }

    /** A direct address: a number, or one of the SFR names A/ACC, B, PSW, SP. */
    private static int direct(String s) {
        switch (s) {
            case "A": case "ACC": return 0xE0;
            case "B":             return 0xF0;
            case "PSW":           return 0xD0;
            case "SP":            return 0x81;
            default:              return parseNumber(s) & 0xFF;
        }
    }

    private static int resolve(String s, Map<String, Integer> labels) {
        String key = s.toUpperCase(Locale.ROOT);
        if (labels.containsKey(key)) return labels.get(key);
        return parseNumber(s) & 0xFFFF;
    }

    private static int checkRel(int rel, String target) {
        if (rel < -128 || rel > 127) {
            throw new AssemblyException("Jump to '" + target + "' is out of range (" + rel + " bytes)");
        }
        return rel & 0xFF;
    }

    /** Parse 25, 0x1F, 1Fh, 0FFH, -3. */
    private static int parseNumber(String t) {
        t = t.trim().toUpperCase(Locale.ROOT);
        try {
            boolean neg = t.startsWith("-");
            if (neg) t = t.substring(1);
            int v;
            if (t.startsWith("0X")) v = Integer.parseInt(t.substring(2), 16);
            else if (t.endsWith("H")) v = Integer.parseInt(t.substring(0, t.length() - 1), 16);
            else v = Integer.parseInt(t, 10);
            return neg ? -v : v;
        } catch (NumberFormatException e) {
            throw new AssemblyException("Not a number / unknown label: " + t);
        }
    }
}
