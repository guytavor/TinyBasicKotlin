
import java.io.File
import java.lang.IllegalStateException
import java.util.*
import kotlin.system.exitProcess

/******************************************************************************
 TinyBasic Interpreter

 see README.md

 ******************************************************************************/


/**
 * Tokenizer
 *
 * Gets a program text as input, and allows iterating over tokens.
 */
class Lexer(private val program: String) {

  private var currentIndex: Int = -1
  private var currentLine: Int = 1
  private var currentPosInLine: Int = 0
  private var currentChar: Char = 0.toChar()

  fun hasToken(): Boolean {
    return currentIndex < program.length
  }

  fun nextToken(): Token {

    if (currentIndex == program.length - 1) {
      ++currentIndex
      return newToken("",  TokenType.EOF)
    }


    nextChar()

    // Skip whitespace
    while (currentChar in charArrayOf(' ', '\t', '\t')) {
      nextChar()
    }

    when (currentChar) {

      '\n' -> return newToken("\n", TokenType.NEWLINE)
      ',' -> return newToken(",", TokenType.COMMA)
      '*' -> return newToken("*",  TokenType.ASTERISK)
      '/' -> return newToken("/",  TokenType.SLASH)
      '+' -> return newToken("+",  TokenType.PLUS)
      '-' -> return newToken("+",  TokenType.MINUS)
      '=' -> return newToken("=",  TokenType.EQ)
      '>' -> {
        return if (peek() == '=') {
          newToken(">=", TokenType.GTEQ)
        } else {
          newToken(">", TokenType.GT)
        }
      }
      '<' -> {
        return when {
          peek() == '>' -> {
            newToken("<>", TokenType.NOTEQ)
          }
          peek() == '=' -> {
            newToken("<=", TokenType.LTEQ)
          }
          else -> {
            newToken("<", TokenType.LT)
          }
        }
      }
      in '0'..'9' -> {
        val startPos = currentIndex
        while (peek().isDigit()) {
          // Get rest of number.
          nextChar()
        }

        return newToken(program.substring(startPos, currentIndex + 1),  TokenType.NUMBER)
      }

      in 'A'..'Z' -> {
        if (peek().isLetter()) {
          // Get rest of symbol.
          val startPos = currentIndex
          while (peek() in 'A'..'Z') {
            nextChar()
          }
          val keyword = program.substring(startPos, currentIndex + 1)
          val keywordToken = getKeywordToken(keyword)
          // Must be a valid keyword.
          if (keywordToken == null) {
            abort("Invalid keyword: $keyword")
          } else {
            return newToken(keyword,  keywordToken)
          }
        } else {
          // Variable name.
          return newToken(currentChar.toString(),  TokenType.VAR)
        }
      }

      '"' -> {
        nextChar()  // eat "
        val startPos = currentIndex
        do {
          nextChar()
        } while (currentChar != '"')
        return newToken(program.substring(startPos, currentIndex), TokenType.STRING)
      }

      else -> abort("Unexpected char: $currentChar")
    }
    abort("Unexpected char: $currentChar")
    throw IllegalStateException("unreachable")
  }

  private fun newToken(string: String, tokenType: TokenType) : Token {
    return Token(string, tokenType, currentLine, currentPosInLine)
  }

  private fun nextChar() {
    currentChar = program[++currentIndex]
    if (currentChar == '\n') {
      currentLine++
      currentPosInLine = 0
    } else {
      currentPosInLine++
    }

  }

  private fun peek(): Char {
    return program[currentIndex + 1]
  }

  private fun abort(message: String) {
    println("Error line: $currentLine at: $currentPosInLine: $message")
    exitProcess(0)
  }

  private fun getKeywordToken(keyword: String): TokenType? {
    return try {
      val token = TokenType.valueOf(keyword)
      if (token.isKeyword) token else null
    } catch (e: IllegalArgumentException) {
      null
    }
  }

}

data class Token(
  val string: String,
  val tokenType: TokenType,
  val line: Int,
  val position: Int)


enum class TokenType(val isKeyword: Boolean) {

  EOF(false),
  VAR(false),
  NEWLINE(false),
  NUMBER(false),
  STRING(false),
  COMMA(isKeyword = false),

  // KEYWORDS.
  END(true),
  GOSUB(true),
  GOTO(true),
  IF(true),
  INPUT(true),
  LET(true),
  PRINT(true),
  REM(true),
  RETURN(true),
  THEN(true),

  // OPERATORS.
  ASTERISK(false),
  EQ(false),
  GT(false),
  GTEQ(false),
  LT(false),
  LTEQ(false),
  MINUS(false),
  NOTEQ(false),
  PLUS(false),
  SLASH(false)
}


/**
 * Creates an AST from lexed code.
 */
class Parser(private val lexer: Lexer) {

  var currentToken = Token("", TokenType.EOF, 0, 0)
  var lookAhead = Token("", TokenType.EOF, 0, 0)

  init {
    nextToken()  // read current and next
  }

  /**
   * Main entry point, parses a program and creates and AST.
   */
  fun parseProgram() : AST {
    val ast = AST(mutableSetOf())
    while (lexer.hasToken()) {
      ast.lines.add(line())
    }
    return ast
  }

  // Parse syntax elements, such as line, statement, expression, etc..


  private fun line() : Line {
    matchNext(TokenType.NUMBER)
    val lineNumber = currentToken.string.toInt()
    val statement = statement()
    if (peekNextToken().tokenType != TokenType.EOF) {
      matchNext(TokenType.NEWLINE)
    }
    return Line(lineNumber = lineNumber, statement = statement)
  }

  private fun statement() :  Statement {
    nextToken()
    when (currentToken.tokenType) {

      TokenType.REM -> {
        while (peekNextToken().tokenType != TokenType.NEWLINE) {
          nextToken()
        }
        return RemStatement()
      }

      TokenType.PRINT -> return PrintStatement(stringOrExpression())

      TokenType.IF -> return IfStatement(comparison(), then(), statement())

      TokenType.GOTO -> return GotoStatement(expression())

      TokenType.INPUT -> return InputStatement(identifier())

      TokenType.LET -> return LetStatement(identifier(), equals(), expression())

      TokenType.GOSUB -> return GosubStatement(expression())

      TokenType.RETURN -> return ReturnStatement()

      TokenType.END -> return EndStatement()
    }
    abort("Unexpected: ${currentToken.string}")
    throw IllegalStateException()
  }

  private fun equals() : Token {
    matchNext(TokenType.EQ)
    return currentToken
  }
  private fun then() : Token {
    matchNext(TokenType.THEN)
    return currentToken
  }

  private fun identifier() : Token {
    matchNext(TokenType.VAR)
    return currentToken
  }

  private fun comparison() : Comparison {
    return Comparison(expression(), relop(), expression())
  }

  private fun relop() : Token {
    nextToken()
    when (currentToken.tokenType) {
      TokenType.EQ, TokenType.GTEQ, TokenType.GT, TokenType.LT, TokenType.LTEQ, TokenType.NOTEQ ->
        return currentToken
      else -> abort("Expected operator, found ${currentToken.tokenType}")
    }
    throw IllegalStateException()
  }

  data class Comparison(val lExpression: Expression, val relop: Token, val rExpression: Expression) {
    fun value(virtualMachine: VirtualMachine): Boolean {
      val lvalue = lExpression.value(virtualMachine)
      val rValue = rExpression.value(virtualMachine)
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

  private fun expression() : Expression {
    val term = term()
    val op: Token?
    val rTerm: Term?

    return when (peekNextToken().tokenType) {
      TokenType.PLUS, TokenType.MINUS -> {
        nextToken()
        op = currentToken
        rTerm = term()
        Expression(term, op, rTerm)
      }
      else -> Expression(term, null, null)
    }
  }

  private fun term() : Term {
    val unary = unary()
    val op: Token?
    val rUnary:  Unary?

    return when (peekNextToken().tokenType) {
      TokenType.ASTERISK, TokenType.SLASH -> {
        nextToken()
        op = currentToken
        rUnary = unary()
        Term(unary, op, rUnary)
      }
      else -> Term(unary, null, null)
    }
  }

  private fun unary() : Unary {
    var op: Token? = null

    when (peekNextToken().tokenType) {
      TokenType.PLUS, TokenType.MINUS -> {
        nextToken()
        op = currentToken
      }
    }
    val primary = primary()
    return Unary(op, primary)
  }

  private fun primary() : Primary {
    nextToken()
    if (currentToken.tokenType != TokenType.VAR && currentToken.tokenType != TokenType.NUMBER) {
      abort("Expecting either a number or variable name, got: ${currentToken.tokenType}")
    }
    return Primary(currentToken)
  }

  data class Primary(val primary: Token) {
    fun value(virtualMachine: VirtualMachine) : Int {
      return when (primary.tokenType) {
        TokenType.VAR -> virtualMachine.getVar(primary.string[0])
        TokenType.NUMBER -> primary.string.toInt()
        else -> throw IllegalStateException("Invalid primary type: $primary")
      }
    }
  }
  data class Unary(val op: Token?, val primary: Primary) {
    fun value(virtualMachine: VirtualMachine) : Int {
      var value =  primary.value(virtualMachine)
      if (op?.tokenType == TokenType.MINUS) {
        value *= -1
      }
      return value
    }

  }
  data class Term(val unary: Unary, val op: Token?, val rUnary: Unary?) {
    fun value(virtualMachine: VirtualMachine) : Int {
      var value = unary.value(virtualMachine)
      if (op != null) {
        value *= if (op.tokenType == TokenType.ASTERISK) {
          rUnary!!.value(virtualMachine)
        } else {
          (1 / rUnary!!.value(virtualMachine))
        }
      }
      return value

    }
  }
  data class Expression(val term: Term, val op: Token?, val rTerm: Term?) {
    fun value(virtualMachine: VirtualMachine) : Int {

      var value = term.value(virtualMachine)
      if (op != null && rTerm != null) {
        value += if (op.tokenType == TokenType.PLUS) {
          rTerm.value(virtualMachine)
        } else {
          -rTerm.value(virtualMachine)
        }
      }
      return value
    }
  }
  private fun stringOrExpression() : StringOrExpression {
    return when (peekNextToken().tokenType) {
      TokenType.STRING -> {
        matchNext(TokenType.STRING)
        StringOrExpression(string = currentToken.string)
      }
      else -> StringOrExpression(expression = expression())
    }
  }

  class StringOrExpression {
    var string: String? = null
    var expression: Expression? = null

    constructor(string: String) {
      this.string = string
    }
    constructor(expression: Expression) {
      this.expression = expression
    }
    fun isString() : Boolean {
      return string != null
    }
    fun isExpression() : Boolean {
      return expression != null
    }

    override fun toString(): String {
      return if (isString()) string!! else expression!!.toString()
    }

    fun value(virtualMachine: VirtualMachine): Any {
      return if (isString()) string!! else expression!!.value(virtualMachine)
    }

  }

  private fun matchNext(tokenType: TokenType) {
    nextToken()
    match(tokenType)
  }

  private fun match(tokenType: TokenType) {
    if (currentToken.tokenType != tokenType) {
      abort("Expected $tokenType, found: ${currentToken.tokenType}")
    }
  }

  private fun nextToken() {
    currentToken = lookAhead
    lookAhead = if (lexer.hasToken()) lexer.nextToken() else Token("", TokenType.EOF, 0, 0)
  }

  private fun peekNextToken() : Token {
    return lookAhead
  }

  private fun abort(message: String) {
    println("Error line: ${currentToken.line} at: ${currentToken.position}: $message")
    exitProcess(0)
  }

  /**
   * Abstract sytax tree represents a program in an abstract object form.
   * In BASIC this is a list of lines.
   */
  data class AST(val lines: MutableSet<Line>)

  data class Line(val lineNumber: Int, val statement: Statement)

  interface Statement {
    fun interpret(virtualMachine: VirtualMachine)
  }

  class ReturnStatement : Statement {
    override fun interpret(virtualMachine: VirtualMachine) {
      virtualMachine.requestReturn()
    }
  }

  class EndStatement : Statement {
    override fun interpret(virtualMachine: VirtualMachine) {
      virtualMachine.halt()
    }

  }

  class RemStatement : Statement {
    override fun interpret(virtualMachine: VirtualMachine) {
      // Do nothing.
    }

  }

  data class GosubStatement(val expression: Expression) : Statement {
    override fun interpret(virtualMachine: VirtualMachine) {
      virtualMachine.jumpSubroutine(expression.value(virtualMachine))
    }

  }

  data class LetStatement(val identifier: Token,
                          val equals: Token,
                          val expression: Expression) : Statement {
    override fun interpret(virtualMachine: VirtualMachine) {
      virtualMachine.setVar(identifier.string[0], expression.value(virtualMachine))
    }

  }

  data class InputStatement(val identifier: Token) : Statement {
    override fun interpret(virtualMachine: VirtualMachine) {
      val value = readLine()!!
      virtualMachine.setVar(identifier.string[0], value.toInt())
    }

  }

  data class GotoStatement(val expression: Expression) : Statement {
    override fun interpret(virtualMachine: VirtualMachine) {
      virtualMachine.jump(expression.value(virtualMachine))
    }

  }

  data class IfStatement(val comparison: Comparison,
                    val then: Token,
                    val statement: Statement) : Statement {
    override fun interpret(virtualMachine: VirtualMachine) {
      if (comparison.value(virtualMachine)) {
        statement.interpret(virtualMachine)
      }
    }

  }

  data class PrintStatement(val stringOrExpression: StringOrExpression) : Statement {
    override fun interpret(virtualMachine: VirtualMachine) {
      println(stringOrExpression.value(virtualMachine))
    }
  }


}

class VirtualMachine {
  private var ast = Parser.AST(mutableSetOf())
  private val variables: MutableMap<Char, Int> = mutableMapOf()  // vars are single chars, only contain int values.
  private var currentLineIndex = -1

  private var jumpRequest: Int? = null  // null or line number of jump target
  private var gosubRequest: Int? = null // null or line number of gosub target
  private var returnAddresses = Stack<Int>()  // stach of addresses to 'RETURN' to.
  private var returnRequest = false  // was a return was requested?
  private var endRequest = false  // was an END requested?

  fun interpret(ast: Parser.AST) {
    this.ast = ast
    currentLineIndex = 0
    do {
      val line = ast.lines.elementAt(currentLineIndex)

      line.statement.interpret(this)

      if (jumpRequest != null) {
        currentLineIndex = getLineIndexByLineNumberOrDie(jumpRequest!!)
        jumpRequest = null
      } else if (gosubRequest != null) {
        returnAddresses.push(currentLineIndex + 1)
        currentLineIndex = getLineIndexByLineNumberOrDie(gosubRequest!!)
        gosubRequest = null
      } else if (returnRequest) {
        currentLineIndex = returnAddresses.pop()
        returnRequest = false
      } else if (endRequest) {
        break
      } else {
        // Just go to the next line.
        currentLineIndex++
        if (ast.lines.size == currentLineIndex) {
          // Reached end of program, without hitting an "END" statement.
          break
        }
      }
    } while (true)
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

  internal fun jump(lineNumber: Int) {
    jumpRequest = lineNumber
  }

  internal fun jumpSubroutine(lineNumber: Int) {
    gosubRequest = lineNumber
  }

  internal fun requestReturn() {
    returnRequest = true
  }

  internal fun halt() {
    endRequest = true
  }

  internal fun setVar(identifier: Char, value: Int) {
    variables[identifier] = value
  }

  internal fun getVar(identifier: Char) : Int {
    val result = variables[identifier]
    if (result == null) {
      abort("Referenced variable $identifier does not exist. use LET first")
      return 0  // Unreachable, abort crashes.
    }
    return result
  }

}


fun main(args: Array<String>) {
  if (args.size != 1) {
    println("Usage: TinyBaseic <filename>")
    exitProcess(1)
  }
  val program = File(args[0]).readText()

  val lexer = Lexer(program)
  val ast = Parser(lexer).parseProgram()

  val interpreter = VirtualMachine()
  interpreter.interpret(ast)
}
