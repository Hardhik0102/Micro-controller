package com.team.ms51sim.logging;

import com.team.ms51sim.ipc.Uds;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Entry point of the <b>Logging process</b>.
 * <pre>  java -cp out com.team.ms51sim.Main --logger</pre>
 */
public final class LoggerProcess {

    private LoggerProcess() {}

    public static void main(String[] args) throws Exception {
        Path sock = Uds.logPath();
        Path logDir = Paths.get(System.getProperty("ms51.logdir", "logs"));
        LogServer server = new LogServer(sock, logDir);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { Files.deleteIfExists(sock); } catch (Exception ignored) { }
        }));
        server.start();
        System.out.println("[logger] pid " + ProcessHandle.current().pid()
                + " listening on " + sock + ", writing " + server.logFile().toAbsolutePath());
        server.awaitShutdown();
        server.close();
        System.out.println("[logger] shut down");
    }
}
