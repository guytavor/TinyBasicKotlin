import kotlin.system.exitProcess

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

  private fun stringOrExpression() : StringOrExpression {
    return when (peekNextToken().tokenType) {
      TokenType.STRING -> {
        matchNext(TokenType.STRING)
        StringOrExpression(string = currentToken.string)
      }
      else -> StringOrExpression(expression = expression())
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

  /**
   * Abstract sytax tree represents a program in an abstract object form.
   * In BASIC this is a list of lines.
   */
  data class AST(val lines: MutableSet<Line>)

  data class Line(val lineNumber: Int, val statement: Statement)

  interface Statement
  class ReturnStatement : Statement
  class EndStatement : Statement
  class RemStatement : Statement
  data class GosubStatement(val expression: Expression) : Statement
  data class LetStatement(val identifier: Token,
                          val equals: Token,
                          val expression: Expression) : Statement

  data class InputStatement(val identifier: Token) : Statement
  data class GotoStatement(val expression: Expression) : Statement
  data class IfStatement(val comparison: Comparison,
                         val then: Token,
                         val thenStatement: Statement) : Statement
  data class PrintStatement(val stringOrExpression: StringOrExpression) : Statement


  data class Comparison(val lExpression: Expression, val relop: Token, val rExpression: Expression)
  data class Primary(val token: Token)
  data class Unary(val op: Token?, val primary: Primary)
  data class Term(val unary: Unary, val op: Token?, val rUnary: Unary?)
  data class Expression(val term: Term, val op: Token?, val rTerm: Term?)
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
    override fun toString(): String {
      return if (isString()) string!! else expression!!.toString()
    }
  }
}
