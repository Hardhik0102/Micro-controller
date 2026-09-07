
| Test | Instruction | Expected Result | Actual Result | Status |
|------|-------------|-----------------|---------------|--------|
| TC01 | MOV A,#7FH | ACC = 7FH | ACC = 7FH | PASS |
| TC02 | MOV R5,A (after MOV A,#42H) | R5 = 42H | R5 = 42H | PASS |
| TC03 | ADD A,R1 (ACC=3, R1=5) | ACC = 08H, CY = 0, P = 1 | ACC = 08H, CY = 0, P = 1 | PASS |
| TC04 | SUBB A,#05H (ACC=3) | ACC = 0FEH, CY = 1 | ACC = 0FEH, CY = 1 | PASS |
| TC05 | ANL A,#0FH (ACC=0ACH) | ACC = 0CH | ACC = 0CH | PASS |
| TC06 | INC A x2 (from 0FFH) | ACC = 01H | ACC = 01H | PASS |
| TC07 | DJNZ R1,loop (ADD A,R2 five times) | ACC = 0FH, R1 = 00H | ACC = 0FH, R1 = 00H | PASS |
| TC08 | HLT (before MOV A,#99H) | halted = true, ACC = 01H | halted = true, ACC = 01H | PASS |
| TC09 | demo1.asm full program | ACC = 0DH, R1 = 00H, R2 = 03H | ACC = 0DH, R1 = 00H, R2 = 03H | PASS |

9 / 9 tests passed.

