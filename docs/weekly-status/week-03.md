# Week 3 Status Update

**Project:** MS51FB9AE Microcontroller Simulator
**Microcontroller:** Nuvoton MS51FB9AE (8051 core)
**Language:** Java
**Week:** 3 — Enhancing Simulator Functionality (Memory, Stack, FIFO Queue)

## Planned work

- Add basic Read/Write memory and show memory in the UI
- Add the Stack Pointer with PUSH and POP, and show the stack in the UI
- Add a FIFO queue with Enqueue and Dequeue, and show the queue in the UI
- Prepare a queue flowchart (enqueue, dequeue, empty, full, status)
- Write an assembly program that validates the queue and record the results
- Write memory, stack and queue test cases and update the documentation

## Completed work

- Added `MOV A,direct` and `MOV direct,A` to read and write internal RAM
- Added a Memory panel in the UI showing a hex dump of RAM `00H` to `7FH`
- Added `PUSH` and `POP` (real 8051 opcodes `C0H` / `D0H`), stack grows up from `SP = 07H`
- Added a Stack panel in the UI showing SP and every pushed byte
- Added a `FifoQueue` class (circular queue, capacity 8) with `ENQ A` and `DEQ A`
- `ENQ` sets `CY = 1` if the queue is full, `DEQ` sets `CY = 1` if the queue is empty
- Added a FIFO Queue panel in the UI showing the queue, size and EMPTY / FULL status
- Redesigned the UI: Memory, Stack and Queue are now shown side by side (not behind tabs), each with its own colour (blue / green / orange), plus a Week 3 banner
- Made the queue flowchart (enqueue, dequeue, empty, full, status update)
- Wrote `queue-demo.asm` — enqueue `11H 22H 33H 44H`, dequeue 4, check FIFO order
- Ran `queue-demo.asm` through the simulator — output order `11H 22H 33H 44H` (correct FIFO)
- Wrote `week3-demo.asm` which exercises memory, stack and queue in one program
- Added 8 new test cases (TC10 to TC17); all 17 tests pass (9 from Week 2 + 8 new)
- Updated the documentation — new instructions, design notes, flowchart, test results

## Pending work

- GPIO, timer and interrupt operations (Week 4)
- Indirect addressing (`MOV A,@Ri`) — not needed yet
- Processes, PCB and CPU scheduling (Week 5 and 6)

## Issues encountered

- The 8051 has no queue instructions, so `ENQ` / `DEQ` use spare opcodes `A6H` / `A7H` (documented)
- Deciding how a full or empty queue should report back — used the carry flag (CY) like real hardware status
- No Week 2 behaviour had to change — the Week 2 engine extended cleanly and all 9 old tests still pass

## Decisions made

- FIFO queue implemented as a circular array (O(1) enqueue and dequeue)
- Full / empty are reported to the program through the CY flag, the simulator does not stop
- Memory uses direct addressing (`MOV A,direct` / `MOV direct,A`), indirect deferred
- Stack follows the real 8051 — SP grows up in internal RAM
- Memory / Stack / Queue shown side by side in the UI, refreshed after every Step
- No Week 2 decision was changed

## Team contribution (Week 3)

- **Hardhik C Shettigar** — memory instructions, stack (PUSH/POP), FifoQueue class, ENQ/DEQ, UI redesign, queue flowchart, queue-demo.asm, week3-demo.asm, 8 new test cases, documentation, integration
- **Mohammed Ardhaan** — no contribution this week
- **Ayman Kolkar** — no contribution this week

## Work accepted

- Memory read/write and the Memory view
- Stack (SP + PUSH / POP) and the Stack view
- FIFO queue (ENQ / DEQ) and the Queue view
- Queue flowchart
- `queue-demo.asm` validation program and its recorded results
- 17 / 17 test cases and updated documentation

## Work carried forward to Week 4

- GPIO, timer and interrupt simulation
- Start planning processes, PCB and the ready queue
- Get the other two team members contributing

## Week 4 plan

- Add simplified GPIO ports (P0 to P3)
- Add a basic 16-bit timer that can overflow
- Add a simple interrupt mechanism (flag checked between instructions)
- Show GPIO / timer / interrupt state in the UI and add test cases
