
import java.lang.IllegalStateException
import kotlin.system.exitProcess

/**
 * Tokenizer
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
      in '1'..'9' -> {
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

data class AST(val lines: MutableList<Line>)
data class Line(val lineNumber: Int, val statement: Statement)
interface Statement {
  fun interpret(interpreter: Interpreter) {

  }

}
class Interpreter {
  val variables: Map<Char, String> = HashMap()
}

/**
 * Creates an AST from lexed code.
 */
class Parser(val lexer: Lexer) {

  var currentToken = Token("", TokenType.EOF, 0, 0)
  var lookAhead = Token("", TokenType.EOF, 0, 0)

  init {
    nextToken()  // read current and next
  }
  fun parseProgram() : AST {
    val ast = AST(mutableListOf())
    while (lexer.hasToken()) {
      ast.lines.add(line())
    }
    return ast
  }

  private fun line() : Line {
    matchNext(TokenType.NUMBER)
    val lineNumber = currentToken.string.toInt()
    val statement = statement()
    if (peekNextToken().tokenType != TokenType.EOF) {
      matchNext(TokenType.NEWLINE)
    }
    return Line(lineNumber = lineNumber, statement = statement)
  }

  private fun statement() : Statement {
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

  }
  data class Unary(val op: Token?, val primary: Primary) {

  }
  data class Term(val unary: Unary, val op: Token?, val rUnary: Unary?) {

  }
  data class Expression(val term: Term, val op: Token?, val rTerm: Term?) {

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

  }


  class ReturnStatement : Statement {

  }

  class EndStatement : Statement {

  }

  class RemStatement : Statement {

  }

  data class GosubStatement(val expression: Expression) : Statement {

  }

  data class LetStatement(val identifier: Token,
                          val equals: Token,
                          val expression: Expression) : Statement {

  }

  data class InputStatement(val identifier: Token) : Statement {

  }

  data class GotoStatement(val expression: Expression) : Statement {

  }

  data class IfStatement(val comparison: Comparison,
                    val then: Token,
                    val statement: Statement) : Statement {

  }

  data class PrintStatement(val stringOrExpression: StringOrExpression) : Statement {
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

}

fun main() {
  val program = """
5 REM "***** My Program ****"
10 PRINT "Hello, world!"
20 PRINT "what is your age?"
30 PRINT 12987*99+15/3
40 INPUT N
70 IF N>80 THEN PRINT "OLD"
80 LET X=13
90 PRINT X
92 LET M=105
95 GOTO M
100 REM "this is a remark"
105 PRINT "JUMPED TO HERE"
110 GOSUB 1000
120 PRINT "RETURNED"
990 END
1000 PRINT "got here"
1010 RETURN

""".trimIndent()

  val lexer = Lexer(program)
//  while (lexer.hasToken()) {
//    println(lexer.nextToken())
//  }
  val ast = Parser(lexer).parseProgram()
  for (line in ast.lines) {
    println("${line.lineNumber}\t${line.statement}")
  }

}
// TODO:
// * decimal numbers, not just integers
