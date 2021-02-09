1 DIM a$(4,4)
2 LET a$(1)="guy"
3 PRINT a$(1);"X" : REM   "guy X"
4 PRINT a$(2,2);"X" : REM " X"
5 PRINT a$(3);"X" : REM   "    X"
6 STOP
10 REM *** DIMS ***
20 DIM Apple(10,10)
35 LET x=12
40 DIM F(x,6,7,8)
50 LET Apple(1,1) = 5
60 PRINT Apple(1,1)
70 PRINT Apple(1,2)
80 LET F(1,2,3,4)=88
90 PRINT F(2,2,3,4)
100 PRINT F(1,2,3,4)
105 DIM C$(12,8) : REM Months
110 LET C$(1)="Hello"
120 PRINT C$(1)
130 PRINT C$(1,5) : REM o
