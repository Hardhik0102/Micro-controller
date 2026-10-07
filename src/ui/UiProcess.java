package com.team.ms51sim.ui;

import com.team.ms51sim.ipc.CoreClient;
import com.team.ms51sim.ipc.Uds;

import javax.swing.JOptionPane;

/**
 * Entry point of the <b>UI process</b> - Swing front-end that talks to the Core
 * process over IPC.
 * <pre>  java -cp out com.team.ms51sim.Main --ui</pre>
 */
public final class UiProcess {

    private UiProcess() {}

    public static void main(String[] args) {
        try {
            CoreClient client = new CoreClient(Uds.corePath(), 5000);
            SimulatorUI.launch(new RemoteSimulator(client),
                    "3 processes: UI <-> Core <-> Logger  (Unix domain sockets)");
        } catch (java.io.IOException e) {
            System.err.println("[ui] cannot reach the Core process: " + e.getMessage());
            try {
                JOptionPane.showMessageDialog(null, "Cannot reach the Core process:\n" + e.getMessage()
                        + "\n\nStart it with --core (or use the launcher).", "IPC error", JOptionPane.ERROR_MESSAGE);
            } catch (java.awt.HeadlessException ignored) { }
            System.exit(1);
        }
    }
}
