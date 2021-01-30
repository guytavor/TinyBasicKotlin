
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
