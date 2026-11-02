
; Week 4 demo program:
; UI -> Core -> CPU / Memory / Stack / Queue -> Logger.
;
; Program size : 16 instructions, 26 bytes
; Expected result (same standalone and over IPC):
;   ACC = 22H    SP = 07H    CPU halted
;   RAM[30H] = 7EH    RAM[31H] = 11H    RAM[32H] = 22H
;   queue = [33H]  (size 1)
; Used by IPC tests IPC-01 to IPC-08 and IT-02.
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
