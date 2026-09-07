package com.team.ms51sim;

/**
 * Static description of one supported machine instruction.
 *
 * <p>An {@code Instruction} is a template: it knows its opcode, mnemonic,
 * functional category, encoded length and the operation to perform. The
 * {@link InstructionSet} holds one of these per opcode in a HashMap so that
 * {@code decode()} is a single map lookup.</p>
 */
public class Instruction {

    /** Functional categories required by the Week 2 brief. */
    public enum Category {
        DATA_TRANSFER("Data Transfer"),
        ARITHMETIC("Arithmetic"),
        LOGICAL("Logical Operation"),
        INC_DEC("Increment / Decrement"),
        CONTROL_FLOW("Control Flow"),
        PROGRAM_TERMINATION("Program Termination"),
        MISC("Miscellaneous");

        public final String label;
        Category(String label) { this.label = label; }
    }

    /** The operation body. Runs during the EXECUTE stage. */
    public interface Operation {
        /**
         * @param cpu     processor state to mutate
         * @param operand the byte that follows the opcode (0 for 1-byte instructions)
         * @param ctx     execution context, used to report register/memory changes
         */
        void run(CPU cpu, int operand, ExecContext ctx);
    }

    public final int opcode;
    public final String mnemonic;      // e.g. "ADD  A,R3"
    public final Category category;
    public final int length;           // 1 or 2 bytes
    public final Operation operation;

    public Instruction(int opcode, String mnemonic, Category category, int length, Operation operation) {
        this.opcode = opcode;
        this.mnemonic = mnemonic;
        this.category = category;
        this.length = length;
        this.operation = operation;
    }

    @Override
    public String toString() {
        return String.format("%02X  %-14s [%s]", opcode, mnemonic, category.label);
    }
}
