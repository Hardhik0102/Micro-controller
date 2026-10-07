# Week 4 - Standalone vs multi-process benchmark and analysis

Reproduce with `./run.sh bench` (`java -cp out com.team.ms51sim.Main --bench report.md [outerLoops] [repetitions]`).
The workload is `programs/benchmark-loop.asm` (nested `DJNZ` loops, 20 523 instructions).

> **Important - measured in a 1-core Linux VM.** The numbers below were produced while preparing this submission on a machine
> with **one** CPU core, so the three processes could never run in parallel. On your laptop (several cores) the multi-process
> numbers, and especially CPU usage above 100 %, will differ. **Run `./run.sh bench` on your own machine and paste the new tables
> before the demo**; the analysis below explains *why* the numbers look as they do and holds either way.

## 1. Results

### Environment

- Date: 2026-10-02T18:21:05
- Java: 21.0.10 (OpenJDK 64-Bit Server VM)
- OS: Linux 6.18.44-fc-v51, 1 CPU core(s) available
- Workload: 20523 instructions per run, median of 7 runs after 5 warm-up runs

### 1. Execution time and CPU usage

| Configuration | Median time (ms) | us / instruction | Instructions / s | CPU time / run (ms) | CPU usage* | Logger catch-up after run (ms) |
|---|---:|---:|---:|---:|---:|---:|
| Standalone  step() | 81.7 | 3.98 | 251,200 | 71 | 102% | - |
| Standalone  run() | 50.8 | 2.47 | 404,150 | 53 | 100% | - |
| Multi  STEP per request, logging ON | 841.5 | 41.00 | 24,388 | 880 | 99% | 20 |
| Multi  STEP per request, logging OFF | 510.4 | 24.87 | 40,210 | 514 | 100% | - |
| Multi  RUN (1 request), logging ON | 100.6 | 4.90 | 204,076 | 120 | 90% | 26 |
| Multi  RUN (1 request), logging OFF | 43.6 | 2.12 | 471,140 | 49 | 104% | - |
| Multi  RUN + full trace returned, logging OFF | 78.7 | 3.84 | 260,672 | 89 | 102% | - |

\* CPU usage = (CPU time of UI-client + Core + Logger processes) / elapsed wall time; 100% = one core fully busy. On a multi-core machine the three processes can run in parallel, so values above 100% are possible.

### 2. IPC latency per STEP request (logging OFF run)

| Metric | Value (us) |
|---|---:|
| Round-trip time (send request -> reply received), mean | 16.8 |
| Round-trip time, median (p50) | 16.0 |
| Round-trip time, p99 | 34.0 |
| Core execution time inside the request (fetch/decode/execute + state capture), mean | 2.2 |
| **IPC overhead per request** (round-trip minus Core execution: serialisation + 2 context switches + socket copy), mean | **14.6** |

### 3. Memory (resident set size)

| Process | RSS (MB) |
|---|---:|
| Standalone simulator (1 JVM, UI-less benchmark) | 126.7 |
| Multi-process: UI/client JVM | 126.1 |
| Multi-process: Core JVM | 146.3 |
| Multi-process: Logger JVM | 72.3 |
| **Multi-process total** | **344.6** |

### 4. Process start-up

Starting Logger + Core as separate JVMs and waiting until both sockets accept connections took **370 ms** (the standalone simulator has no such cost).

## 2. Performance analysis

**What was compared**

* *Standalone*: the Week 3 simulator in one JVM (`step()` loop and `run()`).
* *Multi-process, STEP per request*: the client sends one `REQ_STEP` per instruction - exactly what the GUI does on every click or Run tick.
* *Multi-process, RUN*: one `REQ_RUN` request; the Core executes the whole program and returns once.
* *Logging ON/OFF*: whether the Core sends one log line per executed instruction to the Logger process.

**Findings**

1. **IPC overhead per request is about 15 us.** A STEP round trip takes about 17 us, of which the Core spends about 2 us executing
   the instruction; the other ~15 us is serialisation (each reply carries registers + 256 B RAM + trace record), two context switches
   and the kernel copy through the socket. The p99 round trip (34 us) is about twice the median, i.e. occasional scheduling delays.
2. **Per-instruction request/response is the expensive pattern.** STEP-per-request without logging is about 6x slower than the in-process `step()` loop
   (0.51 s vs 0.08 s for 20 k instructions). This only matters for throughput: the GUI is paced by a human or by the 180 ms Run timer, where 15 us is
   about 0.01 % of the time budget - invisible to the user.
3. **Batching removes the overhead.** One `REQ_RUN` for the whole program costs about the same as the standalone `run()` (differences of a few ms are
   JVM/JIT noise between two different JVMs, not evidence that IPC is free). Returning the full per-step trace costs extra (about 35 ms here) because 20 k `StepResult` records are serialised and decoded.
4. **Logging is the largest single cost, not IPC.** Per-instruction logging adds about 16 us/instruction to STEP mode and about 2.8 us/instruction to RUN mode
   (string formatting in the Core + queueing + the Logger's file writes competing for the same single core). Because the log-sender thread decouples the Core from
   the Logger, the Logger only needs ~20 ms *after* the run ends to catch up. Turning per-step logging off (`REQ_SET_LOGGING`) keeps errors and
   the termination message but removes this cost.
5. **Memory: three JVMs cost about 2.7x one JVM** (about 345 MB vs 127 MB resident). Almost all of that is the fixed JVM baseline (heap, JIT, class metadata) that each process pays, not simulator data
   (the simulator state is a few hundred bytes).
6. **Start-up: about 0.4 s extra** to launch two more JVMs and wait for their sockets.
7. **CPU usage:** on this single-core VM total CPU time (client + Core + Logger) is roughly equal to wall time, i.e. the work was serialised on one core.
   With several cores the Logger and Core can overlap, so wall time for logging-ON runs should shrink while total CPU time stays about the same.

**Trade-offs of the multi-process design**

| Gain | Cost |
|---|---|
| Clean separation of responsibilities and interfaces; each member can develop and test one component | ~15 us per request, 3x JVM memory, ~0.4 s start-up |
| Crash isolation: a Logger crash does not stop execution; a UI crash does not lose CPU state | More failure modes to handle (all tested) |
| Logging off the critical path (async queue) | Events can be dropped if the Logger is down |
| Core could later serve other clients (CLI, tests) | Protocol must be kept in sync between processes |

**Possible optimisations (not implemented)**

* Send only changed RAM bytes (delta) instead of all 256 bytes in each `RSP_STATE`.
* Add a "Run fast" button that uses `REQ_RUN` instead of one request per timer tick.
* Batch several `REQ_STEP`s in one frame (pipelining).
* Use a shared-memory ring buffer for the log stream if logging ever becomes the bottleneck.
