; ============================================================
; CRC-6 demonstration program  (short, branch-free version)
; Generator polynomial: g(D) = D^6 + D^5 + 1   (L = 6)
;   -> without the leading D^6 term: 100001B = 21H
;
; REG (6-bit remainder) is kept RIGHT-justified in bits 5..0 of
; memory location 31H (bits 7,6 are always masked back to 0).
;
; This version tests REG's MSB (bit 5) directly with MOV C,ACC.5
; (no mask-and-compare trick needed), then turns that single CY
; bit into a ready-to-use 0x00/0x21 "masked generator" value with
; two instructions (SUBB's borrow behaviour + ANL), and XORs it
; straight into REG with XRL A,direct. No branching inside either
; loop at all. CLR C is used in the flush loop to force a clean
; 0 bit into REG's shift (the data loop doesn't need this, since
; the DATA byte's own RLC naturally overwrites CY with the real
; next data bit before it's used).
;
; Memory map:
;   30H = DATA    (byte being CRC'd, destroyed/shifted as we go)
;   31H = REG     (the 6-bit CRC remainder, right-justified)
;   32H = scratch (holds the masked generator: 00H or 21H)
;   R2  = loop counter, 8 data bits
;   R3  = loop counter, 6 flush bits
; ============================================================

        MOV  A,#0B2H          ; data byte to checksum = 10110010B
        MOV  30H,A
        MOV  A,#00H
        MOV  31H,A             ; REG = 0
        MOV  A,#08H
        MOV  R2,A              ; loop counter = 8

; ---------------- data-bit phase (8 iterations) ----------------
DBITS:  MOV  A,31H
        MOV  C,ACC.5           ; CY = REG bit5 (old MSB)
        MOV  A,#00H
        SUBB A,#00H            ; A = FFH if CY=1, else 00H
        ANL  A,#21H            ; A = masked generator (21H or 00H)
        MOV  32H,A             ; stash it

        MOV  A,30H             ; shift next data bit (MSB first) into REG
        RLC  A
        MOV  30H,A
        MOV  A,31H
        RLC  A
        ANL  A,#3FH
        XRL  A,32H             ; REG ^= masked generator
        MOV  31H,A

        DJNZ R2,DBITS

; ---------------- flush phase: L=6 zero bits --------------------
        MOV  A,#06H
        MOV  R3,A

FLUSH:  MOV  A,31H
        MOV  C,ACC.5
        MOV  A,#00H
        SUBB A,#00H
        ANL  A,#21H
        MOV  32H,A

        CLR  C                 ; CY := 0 (the bit being flushed in is always 0)
        MOV  A,31H             ; shift in a zero bit (flush)
        RLC  A
        ANL  A,#3FH
        XRL  A,32H
        MOV  31H,A

        DJNZ R3,FLUSH

; ---------------- result ----------------
        MOV  A,31H             ; A = CRC-6 result (right-justified, bits5..0)
        ENQ  A                 ; push it into the FIFO queue so it's visible in the UI
        HLT
