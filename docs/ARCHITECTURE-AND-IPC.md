# Week 4 - Three-process architecture 

## 2. IPC architecture diagram

![IPC architecture](ipc-architecture.png)

(SVG version: `ipc-architecture.svg`)

```mermaid
flowchart LR
    L[Launcher<br/>Team Leader]:::l
    subgraph UI["UI process (Student 1)"]
      SW[SimulatorUI / Swing] --> RS[RemoteSimulator<br/>mirror CPU] --> CC[CoreClient]
    end
    subgraph CORE["Core process (Student 2)"]
      CS[CoreServer] --> SIM[Simulator<br/>fetch-decode-execute]
      SIM --> HW[CPU + RAM + Stack + FIFO Queue]
      CS --> LC[LogClient<br/>BlockingQueue + sender thread]
    end
    subgraph LOG["Logging process (Student 3)"]
      LS[LogServer<br/>writer thread] --> F[(logs/ms51sim.log<br/>logs/ms51sim-errors.log)]
    end
    CC <-- "core.sock  (REQ_* / RSP_STATE)" --> CS
    LC -- "log.sock  (LOG_EVENT, one-way)" --> LS
    L -. spawns .-> UI
    L -. spawns .-> CORE
    L -. spawns .-> LOG
    classDef l fill:#F3F0FF,stroke:#5B3FA8
```

Request path of one **Step** click:

```
UI: Step button -> RemoteSimulator.step() -> CoreClient.step()
      --- core.sock: REQ_STEP --->
Core: CoreServer.dispatch -> Simulator.step()  [fetch(); decode(); execute(); update_Status()]
      -> CPU / RAM / stack / queue change -> LogClient.log(...)  (queued, non-blocking)
      --- core.sock: RSP_STATE (CpuState + StepResult) --->
UI: apply CpuState to mirror CPU -> TraceFormatter -> repaint panes
Core (in parallel): log-sender thread --- log.sock: LOG_EVENT --->  Logger: writer thread -> ms51sim.log
```

