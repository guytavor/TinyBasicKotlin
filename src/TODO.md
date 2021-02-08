
TODO:
-----
* expand so we can compile zx spectrum program covert date and pangolin here:
  https://worldofspectrum.org/ZXBasicManual/zxmanappd.html
  * print semicolon multiple stings
  * add parenthesis to expressions

  * Parser: slice eats its rpar, expressionlist does not. what's the correct?
    let them behave the same.

  * optional maybe later: implement assign to slice? i.e.
    LET a$="I'm the ZX Spectrum"
    LET a$(5 TO 8)="******"
    PRINT a$

* make a lexer distinguish between expression, int_only_expression, string_only_expression
  so that parser code does not contain type validation at all.
* expand to zx spectrum basic?
* rewrite with antlr?
* implement zx spectrum like editor / command line?
* transpile to 'c'?
* compile to assembler?

You are here
------------

