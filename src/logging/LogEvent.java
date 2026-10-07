package com.team.ms51sim.logging;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** One log record sent from the Core (or forwarded from the UI) to the Logger process. */
public record LogEvent(int level, String source, long epochMillis, String message) {

    public static final int DEBUG = 0, INFO = 1, WARN = 2, ERROR = 3;
    private static final String[] NAMES = {"DEBUG", "INFO ", "WARN ", "ERROR"};

    public static String levelName(int level) {
        return NAMES[Math.max(0, Math.min(level, 3))];
    }

    public static LogEvent now(int level, String source, String message) {
        return new LogEvent(level, source, System.currentTimeMillis(), message);
    }

    public void write(DataOutputStream o) throws IOException {
        o.writeByte(level);
        o.writeUTF(source);
        o.writeLong(epochMillis);
        o.writeUTF(message);
    }

    public static LogEvent read(DataInputStream in) throws IOException {
        int level = in.readUnsignedByte();
        String source = in.readUTF();
        long ts = in.readLong();
        String msg = in.readUTF();
        return new LogEvent(level, source, ts, msg);
    }
}
