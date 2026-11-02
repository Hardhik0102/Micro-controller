## Tests

### Week 4 - IPC tests
The IPC test suite lives next to the code so it compiles with the project:
`src/com/team/ms51sim/tests/IpcTests.java`

```bash
./run.sh test        # 23 test cases, prints PASS/FAIL, rewrites docs/week-04/test-results.md
```
On Windows use `run.bat test`.

The test run needs nothing started beforehand. Expected result: **23 / 23 PASS**.

| Tests | Count | What they check |
|---|---|---|
| IPC-01 to IPC-19 | 19 | message encode/decode, LOAD / STEP / RUN / RESET across IPC, memory, stack and FIFO queue results, error handling, concurrent clients, logging |
| IT-01 to IT-04 | 4 | Logger, Core and the client as three separate OS processes, full demo, log file written, Core killed |

All tests use the Week 3 demo program `programs/week3-demo.asm` (16 instructions, 26 bytes), so a correct run ends with ACC=22H, RAM[31H]=11H, RAM[32H]=22H and queue=[33H].

### Earlier weeks
Week 2/3 instruction, memory, stack and queue test records stay in `docs/week-02` and `docs/week-03`.
