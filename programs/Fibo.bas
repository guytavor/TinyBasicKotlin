10 PRINT "How many fibonacci numbers do you want?"
20 INPUT N
30 PRINT "Result:"
40 LET A = 0
50 LET B = 1
60 PRINT A
70 LET C = A + B
80 LET A = B
90 LET B = C
100 LET N = N - 1
100 IF N > 0 THEN GO TO 60
110 STOP
