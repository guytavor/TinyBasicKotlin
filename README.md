TinyBasic Interpreter
-----------------------

This was written as an exercise of manually writing TinyBasic interpreter.
...and to work on my kotlin skills.

Here is the supported syntax in BNF form, adapted from wikipedia: https://en.wikipedia.org/wiki/Tiny_BASIC

    line ::= number statement CR
    statement ::=
      PRINT (string | expression)
      IF comparison THEN statement
      GOTO expression
      INPUT var
      LET var = expression
      GOSUB expression
      RETURN
      END
    var ::= A | B | C ... | Y | Z
    comparison ::= expression (("==" | "<>" | ">" | ">=" | "<" | "<=") expression)+
    expression ::= term {( "-" | "+" ) term}
    term ::= unary {( "/" | "*" ) unary}
    unary ::= ["+" | "-"] primary
    primary ::= number | ident

Some notes:
* Variables can only contain integer values, not strings.
* There is only integer values, not decimal values

In was written in a few hours, with help from:
https://web.eecs.utk.edu/~azh/blog/teenytinycompiler1.html
