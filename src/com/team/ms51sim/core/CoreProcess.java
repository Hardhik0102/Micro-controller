package com.team.ms51sim.core;

import com.team.ms51sim.ipc.Uds;
import com.team.ms51sim.logging.LogClient;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point of the <b>Core process</b>.
 * <pre>  java -cp out com.team.ms51sim.Main --core</pre>
 */
public final class CoreProcess {

    private CoreProcess() {}

    public static void main(String[] args) throws Exception {
        Path sock = Uds.corePath();
        LogClient log = new LogClient(Uds.logPath());
        CoreServer server = new CoreServer(sock, log);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { Files.deleteIfExists(sock); } catch (Exception ignored) { }
        }));
        server.start();
        System.out.println("[core] pid " + ProcessHandle.current().pid() + " listening on " + sock);
        server.awaitShutdown();
        log.flush(2000);
        server.close();
        log.close();
        System.out.println("[core] shut down (log events sent=" + log.sent() + ", dropped=" + log.dropped() + ")");
    }
}
