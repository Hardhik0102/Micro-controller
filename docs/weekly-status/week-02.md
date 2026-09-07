# Weekly Status Report — Week 2

## Work Completed

- we made the CPU with the registers A, B, PC, SP and the flags
- we added the program counter and the program memory
- we wrote 8 instructions: MOV A,#data, MOV Rn,A, ADD A,Rn, SUBB A,#data, ANL A,#data, INC A, DJNZ Rn,rel and HLT
- we made fetch(), decode() and execute() as separate steps
- we built the Java UI with Load, Reset, Step and Run buttons
- the UI shows the registers, flags, PC and the step-by-step trace
- we wrote one demo program and 9 test cases, and all 9 passed

## Current Status

- the basic simulator is working
- all 8 instructions are done and tested
- the UI is working
- the demo program runs fully and stops correctly at HLT

## Problems

- we found a few small code errors while writing the instructions and fixed them
- the 8051 has no stop instruction, so we used the spare opcode A5H as HLT
- getting the carry and overflow flags right for ADD and SUBB took some testing

## Decisions

- we kept only the 8 instructions needed for Week 2
- we used a HashMap for the instruction lookup
- we did not change any Week 1 decision — the language is still Java and the chip is still the Nuvoton MS51FB9AE

## Team Contribution (Week 2)

- **Hardhik (Team Leader):** did all the Week 2 work — built the CPU and registers, wrote the 8 instructions, made fetch/decode/execute, built the Java UI, wrote the demo program and the 9 test cases, and wrote the documentation. All Week 2 commits are from Hardhik.
- **Ayman:** no contribution this week.
- **Ardhaan:** no contribution this week.

## Next Week

- add more instructions
- add memory, stack, GPIO, timer and interrupt operations
- add a memory view to the UI
- make sure all team members commit their work
