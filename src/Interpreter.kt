import java.util.*
import kotlin.system.exitProcess

class Interpreter {
  private var ast = Parser.AST(mutableSetOf())
  private val variables: MutableMap<Char, Int> = mutableMapOf()  // vars are single chars, only contain int values.
  private var currentLineIndex = -1

  private var returnAddresses = Stack<Int>()  // stach of addresses to 'RETURN' to.

  fun run(ast: Parser.AST) {
    this.ast = ast
    currentLineIndex = 0
    do {
      val line = ast.lines.elementAt(currentLineIndex)

      if (interpret(line.statement, ast)) {
        break
      }

    } while (true)
  }

  /**
   * Interprets the given 'statement' returns true iff program should stop.
   */
  private fun interpret(statement: Parser.Statement, ast: Parser.AST): Boolean {

    // Handle statements.
    when (statement) {

      is Parser.PrintStatement -> {
        println(evaluate(statement.stringOrExpression))
      }

      is Parser.IfStatement -> {
        if (evaluate(statement.comparison)) {
          return interpret(statement.thenStatement, ast)
        }
      }

      is Parser.LetStatement -> {
        setVar(statement.identifier.string[0], evaluate(statement.expression))
      }

      is Parser.InputStatement -> {
        val value = readLine()!!
        setVar(statement.identifier.string[0], value.toInt())
      }
    }

    // Handle branching and line advancement statements.
    when (statement) {

      is Parser.GotoStatement -> {
        val lineNumber = evaluate(statement.expression)
        currentLineIndex = getLineIndexByLineNumberOrDie(lineNumber)
      }

      is Parser.GosubStatement -> {
        returnAddresses.push(currentLineIndex + 1)
        val lineNumber = evaluate(statement.expression)
        currentLineIndex = getLineIndexByLineNumberOrDie(lineNumber)
      }

      is Parser.ReturnStatement -> {
        currentLineIndex = returnAddresses.pop()
      }

      is Parser.EndStatement -> {
        return true
      }

      else -> {
        // Just go to the next line.
        currentLineIndex++
        if (ast.lines.size == currentLineIndex) {
          // Reached end of program, without hitting an "END" statement.
          return true
        }
      }
    }
    return false
  }


  private fun evaluate(comparison: Parser.Comparison) : Boolean {
    with (comparison) {
      val lvalue = evaluate(lExpression)
      val rValue = evaluate(rExpression)
      when (relop.tokenType) {
        TokenType.GT -> return lvalue > rValue
        TokenType.GTEQ -> return lvalue >= rValue
        TokenType.LT -> return lvalue < rValue
        TokenType.LTEQ -> return lvalue <= rValue
        TokenType.EQ -> return lvalue == rValue
        TokenType.NOTEQ -> return lvalue != rValue
      }
      throw IllegalStateException("Unsupported operator ${relop.tokenType} in comparison statement")
    }
  }

  private fun evaluate(expression: Parser.Expression) : Int {
    with (expression) {
      var value = evaluate(term)
      if (op != null && rTerm != null) {
        value += if (op.tokenType == TokenType.PLUS) {
          evaluate(rTerm)
        } else {
          -1 * evaluate(rTerm)
        }
      }
      return value
    }
  }

  private fun evaluate(stringOrExpression: Parser.StringOrExpression) : Any {
    with (stringOrExpression) {
      return if (isString()) string!! else evaluate(expression!!)
    }
  }

  private fun evaluate(primary: Parser.Primary) : Int {
    with (primary) {
      return when (primary.token.tokenType) {
        TokenType.VAR -> getVar(token.string[0])
        TokenType.NUMBER -> token.string.toInt()
        else -> throw IllegalStateException("Invalid primary type: $primary")
      }
    }
  }

  private fun evaluate(unary: Parser.Unary) : Int {
    with (unary) {
      var value = evaluate(primary)
      if (op?.tokenType == TokenType.MINUS) {
        value *= -1
      }
      return value
    }
  }

  private fun evaluate(term: Parser.Term) : Int {
    with (term) {
      var value = evaluate(unary)
      if (op != null) {
        value *= if (op.tokenType == TokenType.ASTERISK) {
          evaluate(rUnary!!)
        } else {
          (1 / evaluate(rUnary!!))
        }
      }
      return value
    }
  }

  private fun getLineIndexByLineNumberOrDie(lineNumber: Int) : Int {
    for (i in 0 .. ast.lines.size) {
      if (ast.lines.elementAt(i).lineNumber == lineNumber) {
        return i
      }
    }
    abort("Line number: $lineNumber not found")
    return -1
  }

  private fun abort(message: String) {
    println("$ast.lines. ERROR: $message")
    exitProcess(1)
  }

  private fun setVar(identifier: Char, value: Int) {
    variables[identifier] = value
  }

  private fun getVar(identifier: Char) : Int {
    val result = variables[identifier]
    if (result == null) {
      abort("Referenced variable $identifier does not exist. use LET first")
      return 0  // Unreachable, abort crashes.
    }
    return result
  }

}
