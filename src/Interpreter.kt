import java.util.*

typealias Identifier = String
typealias ForLoopIdentifier = Char

class Interpreter {
  private var ast = Parser.AST(mutableSetOf())
  private val variables: MutableMap<Identifier, Double> = mutableMapOf()  // vars are single chars, only contain int values.
  private var currentStatementIndex = StatementIndex(-1, -1)
  private var currentLineNumber = -1  // The number of the current BASIC program line number
  private var numberOfStatementsInCurrentLine = -1
  private var returnAddresses = Stack<StatementIndex>()  // stack of addresses to 'RETURN' to.
  private var forLoops: MutableMap<ForLoopIdentifier, ForLoopContext> = mutableMapOf()


  fun run(ast: Parser.AST) {
    this.ast = ast
    currentStatementIndex = StatementIndex(0, 0)
    do {
      if (interpretCurrentStatement(ast)) {
        break
      }
    } while (true)
  }

  private fun interpretCurrentStatement(ast: Parser.AST) : Boolean {
    val (currentLineNumber, statements) = ast.lines.elementAt(currentStatementIndex.lineIndex)
    this.currentLineNumber = currentLineNumber
    this.numberOfStatementsInCurrentLine = statements.size
    val statement = statements.elementAt(currentStatementIndex.statementInLineIndex)
    return interpret(statement, ast)
  }

  /**
   * Interprets the given 'statement' returns true iff program should stop.
   */
  private fun interpret(statement: Parser.Statement, ast: Parser.AST) : Boolean {

    // Handle statements.
    when (statement) {

      is Parser.PrintStatement -> {
        // if value is integral, don't print ".0" at the end.
        println(evaluate(statement.stringOrExpression).toString().removeSuffix(".0"))
      }

      is Parser.IfStatement -> {
        if (evaluate(statement.comparison)) {
          return interpret(statement.thenStatement, ast)
        }
      }

      is Parser.LetStatement -> {
        setVar(statement.identifier.string, evaluate(statement.expression))
      }

      is Parser.InputStatement -> {
        val value = readLine()!!
        setVar(statement.identifier.string, value.toDouble())
      }

      is Parser.ForStatement -> {
        val identifier = statement.identifier.string[0]
        forLoops[identifier] = ForLoopContext(getNextStatementIndex(), statement.stepExpression, statement.limit)
        setVar(identifier.toString(), evaluate(statement.initialValue))
      }
    }

    // Handle branching and line advancement statements.
    when (statement) {

      is Parser.NextStatement -> {
        val identifier = statement.identifier.string[0]
        val forLoopContext = forLoops[identifier]
          ?: throw InterpreterException("NEXT without FOR for identifier $identifier", currentLineNumber)

        val step = if (forLoopContext.step != null) {
          evaluate(forLoopContext.step)
        } else {
          1.0
        }
        val value = getVar(identifier.toString()) + step


        currentStatementIndex = if (forLoopContext.reachedLimit) {
          getNextStatementIndex()
        } else {
          setVar(identifier.toString(), value)
          forLoopContext.reachedLimit = (value == evaluate(forLoopContext.limit))
          forLoopContext.loopStartIndex
        }
      }

      is Parser.GoStatement -> {
        when (statement.goType.tokenType) {

          TokenType.TO -> {
            val targetLineNumber = evaluate(statement.expression).toInt()
            currentStatementIndex = StatementIndex(getLineIndexByLineNumberOrDie(targetLineNumber))
          }

          TokenType.SUB -> {
            // Push the next statement information to the returnAddresses stack.
            val returnTo = getNextStatementIndex()
            returnAddresses.push(returnTo)

            // Jump to gosub target.
            val lineNumber = evaluate(statement.expression).toInt()
            currentStatementIndex = StatementIndex(getLineIndexByLineNumberOrDie(lineNumber))
          }

          else -> {
            throw InterpreterException("Expected TO or SUB after GO, but found: ${statement.goType.string}",
              currentLineNumber)
          }
        }

      }

      is Parser.ReturnStatement -> {
        currentStatementIndex = returnAddresses.pop()
      }

      is Parser.StopStatement -> {
        return true
      }

      else -> {
        // Just go to the next statement.
        currentStatementIndex = getNextStatementIndex()
      }
    }
    if (ast.lines.size == currentStatementIndex.lineIndex) {
      // Reached end of program, without hitting an "END" statement.
      return true
    }
    return false
  }

  private fun getNextStatementIndex() =
    if (numberOfStatementsInCurrentLine > currentStatementIndex.statementInLineIndex + 1) {
      StatementIndex(currentStatementIndex.lineIndex, currentStatementIndex.statementInLineIndex + 1)
    } else {
      StatementIndex(currentStatementIndex.lineIndex + 1)
    }


  private fun evaluate(comparison: Parser.Comparison) : Boolean {
    with (comparison) {
      val lvalue = evaluate(lExpression)
      val rValue = evaluate(rExpression)
      return when (relop.tokenType) {
        TokenType.GT -> lvalue > rValue
        TokenType.GTEQ -> lvalue >= rValue
        TokenType.LT -> lvalue < rValue
        TokenType.LTEQ -> lvalue <= rValue
        TokenType.EQ -> lvalue == rValue
        TokenType.NOTEQ -> lvalue != rValue
        else -> {
          throw IllegalStateException("Unsupported operator ${relop.tokenType} in comparison statement")
        }
      }
    }
  }

  private fun evaluate(expression: Parser.Expression) : Double {
    with (expression) {
      var value = evaluate(term)
      if (op != null && rExpression != null) {
        value += if (op.tokenType == TokenType.PLUS) {
          evaluate(rExpression)
        } else {
          -1 * evaluate(rExpression)
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

  private fun evaluate(primary: Parser.Primary) : Double {
    with (primary) {
      return when (primary.token.tokenType) {
        TokenType.VAR -> getVar(token.string)
        TokenType.NUMBER -> token.string.toDouble()
        else -> throw IllegalStateException("Invalid primary type: $primary")
      }
    }
  }

  private fun evaluate(unary: Parser.Unary) : Double {
    with (unary) {
      var value = evaluate(primary)
      if (op?.tokenType == TokenType.MINUS) {
        value *= -1
      }
      return value
    }
  }

  private fun evaluate(term: Parser.Term) : Double {
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
    throw InterpreterException("Line number: $lineNumber not found", currentLineNumber)
  }

  private fun setVar(identifier: Identifier, value: Double) {
    variables[identifier] = value
  }

  private fun getVar(identifier: Identifier): Double {
    return variables[identifier]
      ?: throw InterpreterException("Referenced variable $identifier does not exist. use LET first", currentLineNumber)
  }

  data class StatementIndex(var lineIndex: Int, var statementInLineIndex: Int = 0)
  data class ForLoopContext(val loopStartIndex: StatementIndex,
                            val step: Parser.Expression?,
                            val limit: Parser.Expression,
                            var reachedLimit: Boolean = false)

}

class InterpreterException(message:String, lineNumber: Int) :
  Exception("Runtime error: Line $lineNumber. ERROR: $message")
