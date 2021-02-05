
TODO:
-----
* expand so we can compile zx spectrum program covert date and pangolin here: 
      * dims and string dims
      * string slicing
      * INT function
      * read/data
      * input label semicolon
      * print semicolon multiple stings
      * add parenthesis to expressions

* make a lexer distinguish between expression, int_only_expression, string_only_expression
  so that parser code does not contain type validation at all.
* expand to zx spectrum basic?
* rewrite with antlr?
* implement zx spectrum like editor / command line?
* transpile to 'c'?
* compile to assembler?

You are here
------------
* DIM var and svar - 
  
      * wrote the code for dim, dim reference and string slices.
      * now testing string.bas for slicing 
      * slicing of literal strings <<<<<<
      * after that, test dim reference in Dims.bas
      * optional maybe later: implement assign to slice? i.e.
              LET a$="I'm the ZX Spectrum"
              LET a$(5 TO 8)="******"
              PRINT a$

