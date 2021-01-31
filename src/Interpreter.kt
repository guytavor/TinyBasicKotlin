import java.util.*

typealias Identifier = String
typealias ForLoopIdentifier = Char

class Interpreter {
  private var ast = Parser.AST(mutableSetOf())
  private val variables: MutableMap<Identifier, Value> = mutableMapOf()  // vars are single chars, only contain int values.
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
        setVar(statement.identifier.string, Value(value))
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
          evaluate(forLoopContext.step).toDouble()
        } else {
          1.0
        }
        val value = getVar(identifier.toString()).toDouble() + step


        currentStatementIndex = if (forLoopContext.reachedLimit) {
          getNextStatementIndex()
        } else {
          setVar(identifier.toString(), Value(value))
          forLoopContext.reachedLimit = (value == evaluate(forLoopContext.limit).toDouble())
          forLoopContext.loopStartIndex
        }
      }

      is Parser.GoStatement -> {
        when (statement.goType.tokenType) {

          TokenType.TO -> {
            val targetLineNumber = evaluate(statement.expression).toDouble().toInt()
            currentStatementIndex = StatementIndex(getLineIndexByLineNumberOrDie(targetLineNumber))
          }

          TokenType.SUB -> {
            // Push the next statement information to the returnAddresses stack.
            val returnTo = getNextStatementIndex()
            returnAddresses.push(returnTo)

            // Jump to gosub target.
            val lineNumber = evaluate(statement.expression).toDouble().toInt()
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

  private fun evaluate(expression: Parser.Expression) : Value {
    with (expression) {
      var value = evaluate(term)
      if (op != null && rExpression != null) {
        value = when (op.tokenType) {
          TokenType.PLUS -> {
            value + evaluate(rExpression)
          }  // string or double
          TokenType.MINUS -> {
            if (value.type == Value.Type.STRING) {
              throw InterpreterException("Minus is not defined for strings", currentLineNumber)
            }
            Value(value.toDouble() - evaluate(rExpression).toDouble())
          }
          else -> {
            throw InterpreterException("Undefined operator: ${op.string}", currentLineNumber)
          }
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

  private fun evaluate(primary: Parser.Primary) : Value {
    with (primary) {
      return when (primary.token.tokenType) {
        TokenType.VAR, TokenType.SVAR -> getVar(token.string)
        TokenType.NUMBER -> Value(token.string.toDouble())
        TokenType.STRING -> Value(token.string)
        else -> throw IllegalStateException("Invalid primary type: $primary")
      }
    }
  }

  private fun evaluate(unary: Parser.Unary) : Value {
    with (unary) {
      val value = evaluate(primary)
      if (op?.tokenType == TokenType.MINUS) {
        if (value.type != Value.Type.DOUBLE) {
          throw InterpreterException("Unary minus is not defined for string value", currentLineNumber)
        }
        Value(value.toDouble() * -1)
      }
      return value
    }
  }

  private fun evaluate(term: Parser.Term) : Value {
    with (term) {
      var value = evaluate(unary)
      if (op != null) {
        if (value.type != Value.Type.DOUBLE) {
          throw InterpreterException("* and / are not defined on string values", currentLineNumber)
        }
        value = Value(value.toDouble() * if (op.tokenType == TokenType.ASTERISK) {
          evaluate(rUnary!!).toDouble()
        } else {
          (1 / evaluate(rUnary!!).toDouble())
        })
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

  private fun setVar(identifier: Identifier, value: Value) {
    variables[identifier] = value
  }

  private fun getVar(identifier: Identifier): Value {
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

/**
 * Single class representing both number values and string values.
 * This keeps parsing object tree simpler, but delegate interpreter
 * the work of matching expression types and aborting when incompatible.
 */
class Value {
  enum class Type { DOUBLE, STRING }
  private val memory: String
  val type: Type

  constructor(string: String) {
    this.memory = string
    this.type = Type.STRING
  }
  constructor(double: Double) {
    this.memory = double.toString()
    this.type = Type.DOUBLE
  }

  fun toDouble() : Double = memory.toDouble()
  override fun toString() : String = memory

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Value

    if (memory != other.memory) return false
    if (type != other.type) return false

    return true
  }

  override fun hashCode(): Int {
    var result = memory.hashCode()
    result = 31 * result + type.hashCode()
    return result
  }
}

operator fun Value.compareTo(other: Value) : Int {
  if (this.type != other.type) {
    throw InterpreterException("Can not add ${this.type} to ${other.type}", -1)
  }
  return when (type) {
    Value.Type.DOUBLE -> this.toDouble().compareTo(other.toDouble())
    Value.Type.STRING -> this.toString().compareTo(other.toString())
  }
}


operator fun Value.plus(other: Value) : Value {
  if (this.type != other.type) {
    throw InterpreterException("Can not add ${this.type} to ${other.type}", -1)
  }
  return when (type) {
    Value.Type.DOUBLE -> Value(toDouble() + other.toDouble())
    Value.Type.STRING -> Value(toString() + other)
  }

}