package com.team.ms51sim.ipc;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Helpers for POSIX <b>Unix domain sockets</b> (AF_UNIX, SOCK_STREAM) - the IPC
 * mechanism selected for Week 4. See docs/week-04/ARCHITECTURE-AND-IPC.md for the
 * justification.
 *
 * <p>Every process finds the socket files through the system property
 * {@code ms51.sockdir}, so the launcher can give each run a private directory.</p>
 */
public final class Uds {

    private Uds() {}

    public static Path sockDir() {
        return Paths.get(System.getProperty("ms51.sockdir", "/tmp/ms51sim"));
    }

    public static Path corePath() { return sockDir().resolve("core.sock"); }
    public static Path logPath()  { return sockDir().resolve("log.sock"); }

    /** Bind + listen on a socket file (a stale file from a crashed run is removed first). */
    public static ServerSocketChannel listen(Path socketFile) throws IOException {
        Files.createDirectories(socketFile.getParent());
        Files.deleteIfExists(socketFile);
        ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        server.bind(UnixDomainSocketAddress.of(socketFile));
        return server;
    }

    /** Connect to a socket file, retrying until {@code timeoutMs} (the peer may still be starting). */
    public static SocketChannel connect(Path socketFile, long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        IOException last = null;
        do {
            SocketChannel ch = null;
            try {
                ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                ch.connect(UnixDomainSocketAddress.of(socketFile));
                return ch;
            } catch (IOException e) {
                last = e;
                if (ch != null) try { ch.close(); } catch (IOException ignored) { }
                try { Thread.sleep(40); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } while (System.currentTimeMillis() < deadline);
        throw new IOException("cannot connect to " + socketFile + ": " + (last != null ? last.getMessage() : "timeout"), last);
    }
}
