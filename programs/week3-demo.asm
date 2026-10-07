; week3-demo.asm - Memory, Stack and FIFO Queue (also the Week 4 demo program)
        MOV  A,#7EH      ; ACC = 7EH
        MOV  30H,A       ; memory write: RAM[30H] = 7EH
        PUSH 30H         ; stack: push RAM[30H]
        MOV  A,#00H
        POP  A           ; ACC = 7EH again
        MOV  A,#11H
        ENQ  A           ; queue [11]
        MOV  A,#22H
        ENQ  A           ; queue [11 22]
        MOV  A,#33H
        ENQ  A           ; queue [11 22 33]
        DEQ  A           ; ACC = 11H (FIFO)
        MOV  31H,A
        DEQ  A           ; ACC = 22H
        MOV  32H,A
        HLT              ; queue still holds [33]
