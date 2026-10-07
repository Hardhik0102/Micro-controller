# Week 4 status report - Multi-process simulator and IPC

**Project:** MS51FB9AE (8051-core) simulator in Java    **Branch:** `week-04`    **Status:** ready for review/merge

> Fill in the names/GitHub handles and the commit links in the tables below before submission.

## 1. Planned work
Separate the simulator into UI, Core and Logging processes; choose and justify a POSIX IPC mechanism; implement and test the communication;
compare standalone vs multi-process (execution time, IPC overhead, CPU, memory); update the documentation.

## 2. Completed work
| Deliverable | Where |
|---|---|
| Three-process working simulator | `./run.sh` (GUI) / `./run.sh demo` (headless) |
| IPC architecture diagram | `docs/week-04/ipc-architecture.png` / `.svg`, Mermaid in `ARCHITECTURE-AND-IPC.md` |
| Selected IPC mechanism + justification | **Unix domain sockets (AF_UNIX stream)**, `ARCHITECTURE-AND-IPC.md` section 3 |
| Working inter-process communication | UI <-> Core (`core.sock`), Core -> Logger (`log.sock`), framed binary protocol, section 4 |
| IPC test cases | 23 cases, all PASS - `TEST-CASES-AND-RESULTS.md`, `./run.sh test` |
| Standalone vs multi-process benchmark | `BENCHMARK-AND-ANALYSIS.md`, `./run.sh bench` |
| Performance analysis | `BENCHMARK-AND-ANALYSIS.md` section 2 |
| Threads where appropriate | accept/handler/sender/writer threads, `BlockingQueue` producer-consumer (see architecture section 5) |
| Updated documentation | this folder + README section (below) |

## 3. Individual contributions (each member owns one component)
| Member | Component | Files owned | Tests they run / extend |
|---|---|---|---|
| Student 1 - *name / GitHub* | **UI process** | `ui/SimulatorUI`, `ui/SimulatorBackend`, `ui/LocalBackend`, `ui/RemoteSimulator`, `ui/UiProcess`, `ipc/CoreClient` | manual GUI walk-through; IPC-03/08/11 |
| Student 2 - *name / GitHub* | **Core process** | `core/CoreServer`, `core/CoreProcess`, `ipc/CpuState`, `ipc/StepCodec`, `ipc/CoreReply` (+ Week 1-3 simulator classes) | IPC-04/05/06/07/09/10/14 |
| Student 3 - *name / GitHub* | **Logging process** | `logging/LogEvent`, `LogClient`, `LogServer`, `LoggerProcess`, `LogStats` | IPC-15/16/17/18/19 |
| Team Leader - *name / GitHub* | **Integration** | `ipc/Protocol`, `Wire`, `Uds`, `IpcException` (shared contract), `launcher/*`, `Main`, `tests/IpcTests`, `bench/Benchmark`, docs | IT-01..IT-04, full-system test, benchmark |

Each owner must be able to explain their component's threads, messages and failure handling at the review, and should commit it from their own GitHub account.

## 4. Decisions made
| # | Decision | Reason |
|---|---|---|
| D1 | IPC = Unix domain sockets (stream) | POSIX sockets API, bidirectional, connection-oriented, standard Java (JDK 16+), no native code; see justification |
| D2 | Length-prefixed binary frames | streams have no message boundaries; binary is compact and cheap to parse |
| D3 | The UI keeps a *mirror CPU* filled from `CpuState` | the Week 3 rendering code is reused unchanged |
| D4 | UI assembles, Core only executes machine code | Core stays a pure "processor"; assembly errors are a UI concern (forwarded to the Logger with `REQ_LOG`) |
| D5 | Asynchronous logging through a bounded queue | the execution path never blocks on file or socket I/O |
| D6 | `SimulatorBackend` interface with `LocalBackend` and `RemoteSimulator` | same UI runs standalone and multi-process, so the benchmark compares like with like |
| D7 | Launcher starts the processes in order Logger -> Core -> UI | Core connects to the Logger's socket at start-up |

## 5. Design changes compared with Week 3
* `SimulatorUI` now depends on `SimulatorBackend` instead of a concrete `Simulator`, and shows the process mode in the banner; errors from a lost Core connection are shown in a dialog.
* `Main` has new modes (`--multi` is now the default; the Week 3 behaviour is `--standalone`).
* The simulator classes (`CPU`, `Simulator`, `Alu`, `InstructionSet`, `FifoQueue`, ...) are unchanged.

## 6. Test results
23 of 23 IPC test cases pass (`./run.sh test`); the differential tests show that remote STEP/RUN produce byte-identical traces and CPU states to the in-process simulator.

## 7. Known issues / limitations
1. **UI calls are synchronous on the Swing thread.** Fine at ~15 us per call, but a hung (not crashed) Core would freeze the window; a `SwingWorker` and a read time-out would fix it.
2. `Run` in the GUI sends one `REQ_STEP` per 180 ms timer tick; it does not use `REQ_RUN`.
3. Every `RSP_STATE` carries the full 256 B RAM (simple, not minimal).
4. If the Logger is down, log events are **dropped** (counted) rather than buffered on disk.
5. Unix sockets live in a temporary directory protected by normal file permissions; there is no authentication beyond that.
6. Benchmark numbers in this repo were measured on a 1-core VM - re-run `./run.sh bench` on your own machine.
7. The GUI could not be exercised in the headless environment used to prepare this code; it compiles and the same Core/Logger path is covered by the tests and `--demo-ipc`. Please click through it once (Load, Step, Run, Reset, close window).

## 8. Work not completed
Week 1-3 documents (queue flowchart, Week 3 test record, etc.) are assumed finished by the team and are not part of this change.

## 9. Git workflow for this week
```
git checkout main && git pull
git checkout -b week-04
# each member commits ONLY their own component from their own account, e.g.
#   Student 1:  git add src/com/team/ms51sim/ui  && git commit -m "UI: remote backend + IPC error handling"
#   Student 2:  git add src/com/team/ms51sim/core src/com/team/ms51sim/ipc/CpuState.java ... && git commit -m "Core: socket server"
#   Student 3:  git add src/com/team/ms51sim/logging && git commit -m "Logger: log server + client"
#   Leader:     git add src/com/team/ms51sim/launcher src/com/team/ms51sim/ipc src/com/team/ms51sim/tests src/com/team/ms51sim/bench docs && git commit -m "Integration, tests, benchmark"
./run.sh test && ./run.sh demo      # before merging
git push -u origin week-04          # open a pull request, review, then merge into main
```

## 10. Plan for Week 5
Delta state updates, a "Run fast" button using `REQ_RUN`, asynchronous UI calls with time-outs, and the next features from the course schedule.

---
### README.md section to add
```markdown
## Week 4 - Multi-process simulator
The simulator runs as three processes: **UI** (Swing), **Core** (CPU, memory, stack, FIFO queue) and **Logger**
(execution + error logs). They communicate over POSIX **Unix domain sockets** with a small length-prefixed binary protocol.

    ./build.sh && ./run.sh          # start all three processes
    ./run.sh demo                   # headless demo      ./run.sh test    # 23 IPC tests      ./run.sh bench   # benchmark

Docs: `docs/week-04/` (architecture + IPC choice, test results, benchmark, status report).
```
