import java.util.*
import kotlin.math.floor

typealias Identifier = String
typealias ForLoopIdentifier = Char

class Interpreter {
  private var ast = Parser.AST(mutableSetOf())
  private val variables: MutableMap<Identifier, Value> =
    mutableMapOf()  // vars are single chars, only contain int values.
  private val dims: MutableMap<Identifier, Dim> = mutableMapOf()
  private var currentStatementIndex = StatementIndex(-1, -1)
  private var currentLineNumber = -1  // The number of the current BASIC program line number
  private var numberOfStatementsInCurrentLine = -1
  private var returnAddresses = Stack<StatementIndex>()  // stack of addresses to 'RETURN' to.
  private var forLoops: MutableMap<ForLoopIdentifier, ForLoopContext> = mutableMapOf()
  private var data = Data()


  fun run(ast: Parser.AST) {
    this.ast = ast
    prepareData(ast)
    currentStatementIndex = StatementIndex(0, 0)
    do {
      if (interpretCurrentStatement(ast)) {
        break
      }
    } while (true)
  }

  private fun prepareData(ast: Parser.AST) {
    for (line in ast.lines) {
      for (statement in line.statements) {
        if (statement is Parser.DataStatement) {
          data.add(line.lineNumber, statement.data)
        }
      }
    }
  }

  private fun interpretCurrentStatement(ast: Parser.AST): Boolean {
    val (currentLineNumber, statements) = ast.lines.elementAt(currentStatementIndex.lineIndex)
    this.currentLineNumber = currentLineNumber
    this.numberOfStatementsInCurrentLine = statements.size
    val statement = statements.elementAt(currentStatementIndex.statementInLineIndex)
    return interpret(statement, ast)
  }

  /**
   * Interprets the given 'statement' returns true iff program should stop.
   */
  private fun interpret(statement: Parser.Statement, ast: Parser.AST): Boolean {

    // Handle statements.
    when (statement) {

      is Parser.PrintStatement -> {
        // if value is integral, don't print ".0" at the end.
        println(evaluate(statement.expression).toString().removeSuffix(".0"))
      }

      is Parser.IfStatement -> {
        if (evaluate(statement.comparison)) {
          return interpret(statement.thenStatement, ast)
        }
      }

      is Parser.LetStatement -> {
        with(statement) {
          assign(variableOrDimName, evaluate(expression))
        }
      }

      is Parser.DimStatement -> {
        val dimensions = mutableListOf<Int>()
        for (expression in statement.dimensions) {
          dimensions.add(evaluate(expression).toInt())
        }
        if (statement.identifier.tokenType == TokenType.SVAR) {
          dims[statement.identifier.string] = StringDim(dimensions)
        } else {
          dims[statement.identifier.string] = NumericDim(dimensions)
        }
      }

      is Parser.InputStatement -> {
        val rawValue = readLine()!!
        // Convert input string to the right VarType
        val value: Value = if (statement.identifier.tokenType == TokenType.VAR) {
          Value(rawValue.toDouble())
        } else {
          Value(rawValue)
        }
        setVar(statement.identifier.string, value)
      }

      is Parser.ForStatement -> {
        val identifier = statement.identifier.string[0]
        forLoops[identifier] =
          ForLoopContext(getNextStatementIndex(), statement.stepExpression, statement.limit)
        setVar(identifier.toString(), evaluate(statement.initialValue))
      }

      is Parser.DataStatement -> {
        // We do nothing.
        // Data is read before the program is run. see prepareData() method.
      }

      is Parser.RestoreStatement -> {
        val lineNumber = if (statement.lineNumber != null) {
          statement.lineNumber.string.toInt()
        } else {
          null
        }
        data.restore(lineNumber)
      }

      is Parser.ReadStatement -> {
        statement.variableOrDimNameList.forEach { assign(it, data.read())}
      }

    }

    // Handle branching and line advancement statements.
    when (statement) {

      is Parser.NextStatement -> {
        val identifier = statement.identifier.string[0]
        val forLoopContext = forLoops[identifier]
          ?: throw InterpreterException("NEXT without FOR for identifier $identifier")

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
            throw InterpreterException("Expected TO or SUB after GO, but found: ${statement.goType.string}")
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
      StatementIndex(
        currentStatementIndex.lineIndex,
        currentStatementIndex.statementInLineIndex + 1
      )
    } else {
      StatementIndex(currentStatementIndex.lineIndex + 1)
    }


  private fun assign(variableOrDimName: Parser.VariableOrDimName, value: Value) {
    when {
      variableOrDimName.dimensions != null -> {
        setDim(
          variableOrDimName.identifier.string,
          variableOrDimName.dimensions.map { evaluate(it).toInt() },
          value)
      }
      else -> {
        setVar(variableOrDimName.identifier.string, value)
      }
    }

  }

  private fun evaluate(comparison: Parser.Comparison): Boolean {
    with(comparison) {
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

  private fun evaluate(expression: Parser.Expression): Value {
    with(expression) {
      var value = evaluate(term)
      if (op != null && rExpression != null) {
        value = when (op.tokenType) {
          TokenType.PLUS -> {
            value + evaluate(rExpression)
          }  // string or double
          TokenType.MINUS -> {
            if (value.type == VarType.STRING) {
              throw InterpreterException("Minus is not defined for strings")
            }
            Value(value.toDouble() - evaluate(rExpression).toDouble())
          }
          else -> {
            throw InterpreterException("Undefined operator: ${op.string}")
          }
        }
      }
      return value
    }
  }

  private fun evaluateFunction(name: Token, arguments: List<Parser.Expression>) : Value {
    return when (name.tokenType) {
      TokenType.INT -> {
        throwIncorrectNumberOfArguments(name, arguments, 1)
        val value = evaluate(arguments[0])
        Value(floor(value.toDouble()))  // To integer.
      }
      else -> {
        throw InterpreterException("Unknown function: ${name.string}")
      }
    }
  }

  private fun evaluate(primary: Parser.Primary): Value {
    with(primary) {
      val identifier = token.string

      return when (primary.token.tokenType) {
        TokenType.VAR, TokenType.SVAR,
        TokenType.INT -> {
          val value: Value  = when {
            existVar(identifier) -> {
              getVar(identifier)
            }
            existDim(identifier) -> {
              if (expressionList == null) throw InterpreterException("Dim without indexes.")
              val indexes = expressionList!!.map { evaluate(it).toInt() }
              getDim(identifier, indexes)
            }
            token.tokenType.isKeyword -> {
              // Function.
              if (expressionList == null) {
                throw InterpreterException("Function must take arguments, none were specified.")
              }
              evaluateFunction(token, expressionList!!)
            }
            else -> {
              throw InterpreterException("No such identifier: $identifier")
            }
          }
          maybeSlice(value, slice)
        }
        TokenType.NUMBER -> Value(identifier.toDouble())
        TokenType.STRING -> {
          maybeSlice(Value(identifier), slice)
        }
        else -> throw IllegalStateException("Invalid primary type: $primary")
      }
    }
  }

  private fun maybeSlice(value: Value, slice: Parser.Slice?): Value {
    if (slice == null) {
      return value
    } else if (value.type != VarType.STRING) {
      throw InterpreterException("Can't slice a numeric value")
    }

    val start = if (slice.start != null) {
      evaluate(slice.start).toInt() - 1  // basic is 1 based
    } else {
      0
    }
    val finish = if (slice.finish != null) {
      evaluate(slice.finish).toInt()
    } else {
      value.toString().length
    }
    return Value(value.toString().substring(start, finish))
  }


  private fun evaluate(unary: Parser.Unary): Value {
    with(unary) {
      val value = evaluate(primary)
      if (op?.tokenType == TokenType.MINUS) {
        if (value.type != VarType.NUMERIC) {
          throw InterpreterException("Unary minus is not defined for string value")
        }
        Value(value.toDouble() * -1)
      }
      return value
    }
  }

  private fun evaluate(term: Parser.Term): Value {
    with(term) {
      var value = evaluate(unary)
      if (op != null) {
        if (value.type != VarType.NUMERIC) {
          throw InterpreterException("* and / are not defined on string values")
        }
        value = Value(
          value.toDouble() * if (op.tokenType == TokenType.ASTERISK) {
            evaluate(rUnary!!).toDouble()
          } else {
            (1 / evaluate(rUnary!!).toDouble())
          }
        )
      }
      return value
    }
  }

  private fun getLineIndexByLineNumberOrDie(lineNumber: Int): Int {
    for (i in 0..ast.lines.size) {
      if (ast.lines.elementAt(i).lineNumber == lineNumber) {
        return i
      }
    }
    throw InterpreterException("Line number: $lineNumber not found")
  }

  private fun setVar(identifier: Identifier, value: Value) {
    variables[identifier] = value
  }

  private fun getVar(identifier: Identifier): Value {
    return variables[identifier]
      ?: throw InterpreterException("Referenced variable $identifier does not exist. use LET first")
  }

  private fun existVar(identifier: Identifier): Boolean =
    variables.containsKey(identifier)

  private fun existDim(identifier: Identifier): Boolean =
    dims.containsKey(identifier)

  private fun getDim(identifier: Identifier, indexes: List<Int>): Value {
    return dims[identifier]?.get(indexes)
      ?: throw InterpreterException("Referenced dim $identifier does not exist. use DIM first")
  }

  private fun setDim(identifier: Identifier, indexes: List<Int>, value: Value) {
    dims[identifier]?.set(indexes, value)
      ?: throw InterpreterException("Referenced dim $identifier does not exist. use DIM first")
  }

  private fun throwIncorrectNumberOfArguments(name: Token, arguments: List<Parser.Expression>, num: Int) {
    if (arguments.size != num) {
      throw InterpreterException("${name.tokenType} function takes exactly $num argument but ${arguments.size} were given.")
    }
  }

  data class StatementIndex(var lineIndex: Int, var statementInLineIndex: Int = 0)
  data class ForLoopContext(
    val loopStartIndex: StatementIndex,
    val step: Parser.Expression?,
    val limit: Parser.Expression,
    var reachedLimit: Boolean = false
  )

  interface Dim {
    fun set(indexes: List<Int>, value: Value)
    fun get(indexes: List<Int>): Value
  }

  internal class StringDim(dimensions: List<Int>) : Dim {
    // We store as a sparse list of indexes pointing to chars.
    private var chars = mutableMapOf<List<Int>, Char>()
    private val numberOfDimensions = dimensions.size

    override fun set(indexes: List<Int>, value: Value) {
      when (indexes.size) {
        numberOfDimensions -> {
          // Set char
          if (value.toString().length != 1) {
            throw Interpreter().InterpreterException("Expecting a single char, found a string")
          }
          chars[indexes] = value.toString()[0]
        }
        numberOfDimensions - 1 -> {
          // Set String, we just set all the string's chars.
          val stringChars = value.toString().toCharArray()
          for ((i, char) in stringChars.withIndex()) {
            set(indexes.plus(i + 1), Value(char.toString()))
          }
        }
        else -> {
          throw IndexOutOfBoundsException("Got ${indexes.size} indexes, but Dims has $numberOfDimensions dimensions")
        }
      }
    }

    override fun get(indexes: List<Int>): Value {
      return when (indexes.size) {
        numberOfDimensions -> {
          // Get char
          Value(chars.getOrDefault(indexes, "").toString())
        }
        numberOfDimensions - 1 -> {
          // Get String.
          val builder = StringBuilder()

          var i = 1  // Basic string index is 1-based.
          do {
            val char = get(indexes.plus(i)).toString()
            builder.append(char)
            i += 1
          } while (char.isNotEmpty())

          Value(builder.toString())
        }
        else -> {
          throw IndexOutOfBoundsException("Got ${indexes.size} indexes, but Dims has $numberOfDimensions dimensions")
        }
      }
    }
  }

  class NumericDim(dimensions: List<Int>) : Dim {
    // We store the Dim sparsely in memory:
    // mapping dimension indexes to a value.
    // i.e. for DIM a(10,10), we keep a list
    // that maps array[2] to values
    // so for getting a(7,7) - we return the value mapped to arrayOf(7,7).
    // TODO: check and throw if dimenstionss out of bounds
    private var dim = mutableMapOf<List<Int>, Value>()

    override fun set(indexes: List<Int>, value: Value) {
      dim[indexes] = value
    }

    override fun get(indexes: List<Int>): Value =
      dim.getOrDefault(indexes, Value(0.0))

  }

  inner class Data {
    private var data: MutableList<Value> = mutableListOf()
    private var dataRestoreIndexes: MutableMap<Int /* lineNumber*/, Int /* data list index*/> = mutableMapOf()
    private var dataCurrentReadPoint: Int = 0

    fun add(lineNumber: Int, elements: List<Value>) {
      dataRestoreIndexes[lineNumber] = data.size
      data.addAll(elements)
    }

    fun read() : Value =
      data[dataCurrentReadPoint++]

    fun restore(givenLineNumber: Int?) {
      // use the first data on or after the specified lineNumber.
      // if not specified, jump to start.
      val lineNumber = givenLineNumber ?: 0
      val foundLineNumber = dataRestoreIndexes.toSortedMap().keys.first { it >= lineNumber }
      dataCurrentReadPoint = dataRestoreIndexes[foundLineNumber]
        ?: throw InterpreterException("Invalid RESTORE line number: $lineNumber")
    }
  }



  inner class InterpreterException(message: String) :
    Exception("Runtime error: Line $currentLineNumber. ERROR: $message")

}


/** Each BASIC variable can be either numeric or string */
enum class VarType { NUMERIC, STRING }


/**
 * Single class representing both number values and string values.
 * This keeps parsing object tree simpler, but delegate interpreter
 * the work of matching expression types and aborting when incompatible.
 */
class Value {
  private val memory: String
  val type: VarType

  constructor(string: String) {
    this.memory = string
    this.type = VarType.STRING
  }
  constructor(double: Double) {
    this.memory = double.toString()
    this.type = VarType.NUMERIC
  }

  fun toDouble() : Double = memory.toDouble()
  fun toInt() : Int = toDouble().toInt()

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
    throw IllegalStateException("Can not compare ${this.type} to ${other.type}")
  }
  return when (type) {
    VarType.NUMERIC -> this.toDouble().compareTo(other.toDouble())
    VarType.STRING -> this.toString().compareTo(other.toString())
  }
}


operator fun Value.plus(other: Value) : Value {
  if (this.type != other.type) {
    throw IllegalStateException("Can not add ${this.type} to ${other.type}")
  }
  return when (type) {
    VarType.NUMERIC -> Value(toDouble() + other.toDouble())
    VarType.STRING -> Value(toString() + other)
  }
}