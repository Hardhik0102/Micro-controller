# Week 4 - IPC test cases and results

Run with `./run.sh test` (`src/com/team/ms51sim/tests/IpcTests.java`, no external framework).
Result of the last run: **23 / 23 PASS** (run three times in a row to check for timing-dependent failures).

The suite has three layers:

1. **Codec tests (IPC-01, IPC-02)** - messages survive serialisation unchanged.
2. **Protocol tests (IPC-03 ... IPC-19)** - Core and Logger run inside the test JVM on their own threads but talk over **real Unix domain sockets**.
3. **Integration tests (IT-01 ... IT-04)** - Logger and Core are started as **separate OS processes** (`ProcessHarness`).

The key idea of IPC-04 / IPC-05 is a *differential test*: the same program is executed by an in-process `Simulator` and by the remote Core, and
the formatted FETCH/DECODE/EXECUTE trace plus the full CPU state (registers, PSW, 256 B RAM, queue) must be identical after every instruction.
That proves the process separation did not change the simulator's behaviour.

| Test | Description | Expected result | Actual result | Status |
|------|-------------|-----------------|---------------|--------|
| IPC-01 | StepResult survives encode/decode (all 16 demo instructions) | 16 identical | 16 identical | PASS |
| IPC-02 | CpuState (regs, RAM, queue) survives encode/decode | true | true | PASS |
| IPC-03 | LOAD returns reset state: PC=0000H, length 26, not finished | PC=0 len=26 finished=false | PC=0 len=26 finished=false | PASS |
| IPC-04 | Remote STEP == local step for every instruction (trace text + CPU state) | 16/16 equal | 16/16 equal | PASS |
| IPC-05 | Remote RUN == local run (instruction count, trace, final state) | true | true | PASS |
| IPC-06 | FIFO order across IPC: RAM[31H]=11H, RAM[32H]=22H, queue=[33H] | 11H 22H [33H] | 11H 22H [33H] | PASS |
| IPC-07 | Stack across IPC: after PUSH SP=08H & RAM[08H]=7EH; after POP SP=07H | SP=08H RAM=7EH \| SP=07H | SP=08H RAM=7EH \| SP=07H | PASS |
| IPC-08 | RESET restores power-on state (PC=0, ACC=0, RAM[30H]=0, queue empty) | PC=0 ACC=0 RAM30=0 q=0 | PC=0 ACC=0 RAM30=0 q=0 | PASS |
| IPC-09 | Unknown opcode A8H is reported as invalid (no crash) | invalid=true error=Unknown opcode A8H at 0000H | invalid=true error=Unknown opcode A8H at 0000H | PASS |
| IPC-10 | STEP after HLT returns 'CPU is halted' and CPU stays halted | error=CPU is halted halted=true | error=CPU is halted halted=true | PASS |
| IPC-11 | Unknown request type -> RSP_ERROR, connection still usable | IpcException then ping OK | IpcException then ping OK | PASS |
| IPC-12 | Corrupt frame (absurd length) closes that connection only; Core keeps serving | dropped, new client OK | dropped, new client OK | PASS |
| IPC-13 | Client disconnects abruptly mid-session; Core keeps its state for the next client | PC=2 kept | PC=2 kept | PASS |
| IPC-14 | 4 concurrent clients x 100 STEPs == 400 sequential local steps | true | true | PASS |
| IPC-15 | No log loss: Logger total == events the Core sent | equal | equal | PASS |
| IPC-16 | UI-side error is forwarded UI -> Core -> Logger as an ERROR line | found in errors file | found in errors file | PASS |
| IPC-17 | Invalid opcode is logged as ERROR; errors file holds only WARN/ERROR | ok | ok | PASS |
| IPC-18 | Per-step logging OFF: a 16-instruction run adds only 3 events (LOAD, HLT, RUN) | 3 | 3 | PASS |
| IPC-19 | Logger missing: Core still executes correctly and counts dropped events | CPU ok, dropped>0 | CPU ok, dropped>0 | PASS |
| IT-01 | Logger, Core and this client are three different OS processes | 3 distinct PIDs | 3 distinct PIDs | PASS |
| IT-02 | Full demo across real processes gives the Week 3 results | ACC=22H R31=11H R32=22H q=[33H] HLT | ACC=22H R31=11H R32=22H q=[33H] HLT | PASS |
| IT-03 | Logger process wrote the log file for the run | ms51sim.log has LOAD + HLT lines | ms51sim.log has LOAD + HLT lines | PASS |
| IT-04 | Core process killed -> UI-side call fails with IOException (UI shows an error) | IOException | IOException | PASS |

## Mapping to the Week 4 requirements

| Requirement | Test cases |
|---|---|
| Communication UI -> Core works | IPC-03, 04, 05, 08 |
| CPU / Memory / Stack / Queue still correct after separation | IPC-04, 05, 06, 07 (FIFO order, PUSH/POP, memory write) |
| Core -> Logger communication works | IPC-15, 16, 17, 18, IT-03 |
| Error handling across processes | IPC-09, 10, 11, 12, 13, 19, IT-04 |
| Concurrency safety | IPC-14 |
| Three separate processes | IT-01, IT-02 |
