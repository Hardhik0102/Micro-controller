package com.team.ms51sim;

/**
 * A fixed-capacity <b>FIFO (First-In, First-Out) queue</b>, implemented as a
 * <b>circular queue</b> over a plain {@code int[]}.
 *
 * <p>This is one of the data structures the project requires (Week 3). It is
 * used by the {@code ENQ} / {@code DEQ} instructions and shown live in the UI.
 * Values are kept masked to 8 bits so it behaves like a byte FIFO on the
 * 8-bit device.</p>
 *
 * <pre>
 *   head --> oldest element (next to leave on DEQ)
 *   tail --> next free slot  (where the next ENQ writes)
 *   count = number of elements currently stored
 * </pre>
 */
public final class FifoQueue {

    private final int[] buf;
    private int head;   // index of the front element
    private int tail;   // index of the next free slot
    private int count;  // elements currently stored

    public FifoQueue(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1");
        this.buf = new int[capacity];
    }

    public int capacity() { return buf.length; }
    public int size()     { return count; }
    public boolean isEmpty() { return count == 0; }
    public boolean isFull()  { return count == buf.length; }

    /**
     * Enqueue one byte at the tail.
     * @return {@code true} if stored, {@code false} if the queue was full.
     */
    public boolean enqueue(int value) {
        if (isFull()) return false;
        buf[tail] = value & 0xFF;
        tail = (tail + 1) % buf.length;
        count++;
        return true;
    }

    /**
     * Dequeue one byte from the head.
     * @return the byte (0-255), or {@code -1} if the queue was empty.
     */
    public int dequeue() {
        if (isEmpty()) return -1;
        int v = buf[head];
        head = (head + 1) % buf.length;
        count--;
        return v;
    }

    /** Front element without removing it, or {@code -1} if empty. */
    public int peek() {
        return isEmpty() ? -1 : buf[head];
    }

    public void clear() {
        head = tail = count = 0;
    }

    /** Elements in front-to-back order (for display). */
    public int[] snapshot() {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = buf[(head + i) % buf.length];
        }
        return out;
    }

    /**
     * Packed status byte readable by software:
     * <pre>
     *   bit 0    : EMPTY (1 = empty)
     *   bit 1    : FULL  (1 = full)
     *   bits 4-7 : current element count
     * </pre>
     */
    public int statusByte() {
        int s = 0;
        if (isEmpty()) s |= 0x01;
        if (isFull())  s |= 0x02;
        s |= (count & 0x0F) << 4;
        return s & 0xFF;
    }
}
