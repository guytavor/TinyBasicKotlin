10 REM Guess the number
20 INPUT a: CLS
30 INPUT "Guess the number", b
40 IF b=a THEN PRINT "That is correct": STOP
50 IF b<a THEN PRINT "That is too small, try again"
60 IF b>a THEN PRINT "That is too big, try again"
70 GO TO 30
