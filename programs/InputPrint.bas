110 INPUT "day?";day
120 INPUT "month?";month
130 INPUT "year (20th century only)?";year
140 PRINT day;"/";month;"/";year
510 INPUT "again?", a$
520 IF a$="n" THEN GO TO 540
530 IF a$ <> "N" THEN GO TO 110
540 INPUT "yards?",yd,"feet?",ft, "inches?",in