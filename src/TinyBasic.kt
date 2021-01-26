
import java.io.File
import kotlin.system.exitProcess

/******************************************************************************
 TinyBasic Interpreter

 see README.md

 ******************************************************************************/


fun main(args: Array<String>) {
  if (args.size != 1) {
    println("Usage: TinyBasic <filename>")
    exitProcess(1)
  }
  val program = File(args[0]).readText()

  val lexer = Lexer(program)
  val ast = Parser(lexer).parseProgram()

  val interpreter = Interpreter()
  interpreter.run(ast)
}


// TODO:
// expand so we can compile zx spectrum program covert date and pangolin here: https://worldofspectrum.org/ZXBasicManual/zxmanappd.html
// multi statement in lin
// string vars
// dims and string dims
// for loop
// read/data
// input label semicolon
// var labels more than one char (string no)
// GOTO -> GO TO
// GOSUB -> GO SUB
// print semicolon multiple stings
// double maths, not just ints

// * expand to zx spectrum basic?
// * rewrite with antlr?
// * implement zx spectrum like editor / command line?
// * transpile to 'c'?
// * compile to assembler?
