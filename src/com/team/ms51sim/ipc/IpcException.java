package com.team.ms51sim.ipc;

import java.io.IOException;

/** The peer answered a request with an RSP_ERROR frame. */
public class IpcException extends IOException {
    public IpcException(String message) { super(message); }
}
