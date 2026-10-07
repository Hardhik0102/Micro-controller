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
