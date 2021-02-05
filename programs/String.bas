1 LET J$="abcdef"
1 PRINT J$(2 TO 5)
2 PRINT J$( TO 3)
3 PRINT J$(4 TO)
4 PRINT J$
10 LET a$="ABLE WAS 1"
20 FOR n=1 TO 10
30 PRINT a$(n TO 10)
40 NEXT n
60 FOR n=1 TO 9
70 PRINT a$(10-n TO 10)
80 NEXT n
90 PRINT "*********************************"
100 PRINT "abcdef"( TO 5)  : REM "abcde"
120 PRINT "abcdef"(2 TO 5) : REM "bcde"
130 PRINT "abcdef"(3 TO 3) : REM "c"
140 LET a$="abcdef"
150 FOR n=1 TO 6
160 PRINT a$(n TO 6)
170 NEXT n
190 IF "b" > "a" THEN PRINT "OK"
200 LET X$="Guy Tavor"
220 PRINT X$(1 TO 3)
230 PRINT X$ + " King"
240 LET Y$ = "Hello"
250 PRINT X$ + " " + Y$
255 PRINT "Your name please:"
260 INPUT N$
270 PRINT N$
280 IF N$="Guy" THEN PRINT "YOU ARE GUY!"
290 IF N$<>"Guy" THEN PRINT "YOU ARE *NOT* GUY!"
