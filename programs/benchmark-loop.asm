; benchmark-loop.asm - 3 + 513*N instructions (N = 40 -> 20 523). Used by --bench.
        MOV  A,#28H      ; N = 40
        MOV  R2,A
OUTER:  MOV  A,#0FFH
        MOV  R1,A
INNER:  INC  A
        DJNZ R1,INNER
        DJNZ R2,OUTER
        HLT
