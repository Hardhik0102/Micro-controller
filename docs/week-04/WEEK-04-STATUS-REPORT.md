# Week 4 Status Update

**Project:** Educational Microcontroller Simulator with Process Scheduling
**Week:** 4 - Multi-Process Simulator and IPC
**Team Leader:** Hardhik C Shettigar

---

## Planned work
- Split the existing simulator into three separate processes: UI, Core and Logging
- Use threads where needed inside the processes
- Choose a suitable POSIX IPC mechanism and write why
- Make the three processes talk to each other
- Test the communication (IPC test cases)
- Compare the standalone simulator with the multi-process simulator (execution time, IPC overhead, CPU usage, memory usage)
- Update the GitHub documentation

## Completed work
- Split the simulator into three processes:
  - **UI process:** the window with Load, Step, Run and Reset, and the registers, memory, stack and queue display
  - **Core process:** CPU, Memory, Stack and FIFO Queue, which runs the instructions (fetch, decode, execute)
  - **Logging process:** writes `execution.log` and `error.log`
- Chose **Unix domain sockets (POSIX AF_UNIX)** as the IPC mechanism and wrote the comparison with pipes, named pipes, message queues, shared memory and TCP
- Made a simple message format (each field sent as length + bytes) and a command set: ping, load, step, run, reset, state, flushlog, shutdown
- Used threads in all three processes:
  - UI: Swing thread, Run timer, log-sender thread
  - Core: accept thread, one handler thread per client, log-sender thread (methods are synchronized)
  - Logger: accept thread, one handler thread per client, one writer thread
- Used queues to connect the threads so that logging never slows the CPU
- Made the same UI work in both modes (standalone and multi-process), so the comparison is fair
- Wrote 18 IPC test cases (wrong input, very large message, two clients at once, Logger killed, Core killed, results same as standalone)
- Wrote the benchmark program and analysed standalone vs multi-process
- Organised the repository with one folder per process and wrote the Week 4 documentation (design, diagram, IPC choice, tests, benchmark)

## Pending work
- Individual component work from Mohammed Ardhaan and Ayman Kolkar (UI and Logging parts)
- Decision from the guide on team size (we are 3 members, the brief expects 4)
- Final check of benchmark numbers on the demo machine

## Issues encountered
- Java cannot use POSIX message queues or shared memory without native code, so Unix domain sockets were used
- A socket is a stream and has no message boundaries, so I added the length in front of every field
- Logging every instruction slows down long runs, so I measured it separately and added a rule: a slow Logger makes the Core wait up to 1 second, a dead Logger only causes dropped (and counted) lines
- Running the three processes from VS Code needed the launcher to find the compiled classes itself
- **Team contribution:** only I (Team Leader) have committed code. Mohammed Ardhaan and Ayman Kolkar have not contributed yet, so I had to write all three process components myself. This does not match the Week 4 rule that each member builds one component.

## Decisions made
- **IPC mechanism:** Unix domain sockets, because they are two-way, work with many clients, show immediately when a process dies, and need no native code in Java
- **Core keeps its state** when the UI is closed, so a new UI can connect and continue
- **Logging rule:** wait up to 1 second if the Logger is slow; drop and count lines only if the Logger is down
- **The UI asks for trace text only when it needs it**, to keep messages small
- Keep both Step (one instruction per message) and Run (many instructions in one message), because the cost of IPC is paid per message

## Individual contributions
- **Hardhik C Shettigar (Team Leader):** design, IPC layer, UI process, Core process, Logging process, launcher, tests, benchmark, documentation
- **Mohammed Ardhaan:** no contribution yet
- **Ayman Kolkar:** no contribution yet

## Work accepted
- Three-process simulator (UI, Core, Logger)
- IPC design with the chosen mechanism and justification
- IPC test cases
- Standalone vs multi-process benchmark and analysis
- Updated documentation
