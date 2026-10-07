package com.team.ms51sim.ipc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;

/**
 * Length-prefixed message framing on top of a stream socket.
 *
 * <pre>
 *   +----------------+-----------+---------------------+
 *   | int32 length N | uint8 type| N bytes of payload  |
 *   +----------------+-----------+---------------------+
 * </pre>
 * A stream socket has no message boundaries, so every message carries its own
 * length. All integers are big-endian ({@link DataOutputStream}).
 */
public final class Wire implements Closeable {

    /** Upper bound on one payload - protects against corrupt length fields. */
    public static final int MAX_PAYLOAD = 16 * 1024 * 1024;

    /** One received message. */
    public record Frame(int type, byte[] payload) {
        public DataInputStream reader() {
            return new DataInputStream(new java.io.ByteArrayInputStream(payload));
        }
    }

    /** Builds a payload with the usual DataOutputStream calls. */
    public interface PayloadWriter {
        void write(DataOutputStream out) throws IOException;
    }

    private final SocketChannel channel;
    private final DataInputStream in;
    private final DataOutputStream out;

    public Wire(SocketChannel channel) {
        this.channel = channel;
        this.in = new DataInputStream(new BufferedInputStream(Channels.newInputStream(channel), 16 * 1024));
        this.out = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel), 16 * 1024));
    }

    public static byte[] build(PayloadWriter w) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(128);
        DataOutputStream dos = new DataOutputStream(bos);
        w.write(dos);
        dos.flush();
        return bos.toByteArray();
    }

    public static final byte[] EMPTY = new byte[0];

    public synchronized void send(int type, byte[] payload) throws IOException {
        sendNoFlush(type, payload);
        out.flush();
    }

    /** Queue a frame without flushing - lets a sender batch many frames into one write. */
    public synchronized void sendNoFlush(int type, byte[] payload) throws IOException {
        out.writeInt(payload.length);
        out.writeByte(type);
        out.write(payload);
    }

    public synchronized void flush() throws IOException {
        out.flush();
    }

    public Frame receive() throws IOException {
        int len = in.readInt();                       // EOFException when the peer closed
        if (len < 0 || len > MAX_PAYLOAD) {
            throw new ProtocolException("bad frame length " + len);
        }
        int type = in.readUnsignedByte();
        byte[] payload = new byte[len];
        in.readFully(payload);
        return new Frame(type, payload);
    }

    /** Raw access for tests that need to send deliberately malformed bytes. */
    public DataOutputStream rawOut() { return out; }

    @Override
    public void close() {
        try { channel.close(); } catch (IOException ignored) { }
    }
}
