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
      throw ParserException("Expected $tokenType, found: ${currentToken.tokenType}",
        currentToken.line, currentToken.position)
    }
  }

  private fun nextToken() {
    currentToken = lookAhead
    lookAhead = if (lexer.hasToken()) lexer.nextToken() else Token("", TokenType.EOF, 0, 0)
  }

  private fun peekNextToken() : Token = lookAhead


  // Parse syntax elements, such as line, statement, expression, etc..


  private fun line() : Line {
    matchNext(TokenType.NUMBER)
    val lineNumber = currentToken.string.toInt()
    val statements = statements()
    if (peekNextToken().tokenType != TokenType.EOF) {
      matchNext(TokenType.NEWLINE)
    }
    return Line(lineNumber = lineNumber, statements = statements)
  }

  private fun statements() : List<Statement> {
    val statements = mutableListOf<Statement>()
    do {
      val statement = statement()
      statements.add(statement)
      if (peekNextToken().tokenType == TokenType.COLON) {
        nextToken()  // eat colon.
      }
    }
    while (peekNextToken().tokenType != TokenType.NEWLINE)
    return statements
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

      TokenType.FOR -> {

        val identifier = identifier()
        val equals = equals()
        val initialValue = expression()
        val toToken = toToken()
        val limit = expression()
        var step: Token? = null
        var stepExpression: Expression? = null

        if (peekNextToken().tokenType == TokenType.STEP) {
            step = step()
            stepExpression = expression()
        }

        return ForStatement(
          identifier, equals, initialValue, toToken, limit, step, stepExpression)
      }

      TokenType.NEXT -> return NextStatement(identifier())

      TokenType.PRINT -> return PrintStatement(stringOrExpression())

      TokenType.IF -> return IfStatement(comparison(), then(), statement())

      TokenType.GO -> return GoStatement(goType(), expression())

      TokenType.INPUT -> return InputStatement(identifier())

      TokenType.LET -> return LetStatement(identifier(), equals(), expression())


      TokenType.RETURN -> return ReturnStatement()

      TokenType.STOP -> return StopStatement()
    }
    throw ParserException("Unexpected: ${currentToken.string}", currentToken.line, currentToken.position)
  }

  private fun goType(): Token {
    nextToken()
    if (!arrayOf(TokenType.TO, TokenType.SUB).contains(currentToken.tokenType )) {
      throw ParserException("Expected either TO or SUB after GO", currentToken.line, currentToken.position)
    }
    return currentToken
  }

  private fun toToken() : Token {
    matchNext(TokenType.TO)
    return currentToken
  }
  private fun step() : Token {
    matchNext(TokenType.STEP)
    return currentToken
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

  private fun comparison() = Comparison(expression(), relop(), expression())


  private fun relop() : Token {
    nextToken()
    when (currentToken.tokenType) {
      TokenType.EQ, TokenType.GTEQ, TokenType.GT, TokenType.LT, TokenType.LTEQ, TokenType.NOTEQ ->
        return currentToken
      else -> throw ParserException("Expected operator, found ${currentToken.tokenType}", currentToken.line, currentToken.position)
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

  private fun stringOrExpression() : StringOrExpression =
    when (peekNextToken().tokenType) {
      TokenType.STRING -> {
        matchNext(TokenType.STRING)
        StringOrExpression(string = currentToken.string)
      }
      else -> StringOrExpression(expression = expression())
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
      throw ParserException("Expecting either a number or variable name, got: ${currentToken.tokenType}",
        currentToken.line, currentToken.position)
    }
    return Primary(currentToken)
  }

  /**
   * Abstract sytax tree represents a program in an abstract object form.
   * In BASIC this is a list of lines.
   */
  data class AST(val lines: MutableSet<Line>)

  data class Line(val lineNumber: Int, val statements: List<Statement>)

  interface Statement
  class ReturnStatement : Statement
  class StopStatement : Statement
  class RemStatement : Statement
  data class ForStatement(val identifier: Token,
                          val equals: Token,
                          val initialValue: Expression,
                          val to: Token,
                          val limit: Expression,
                          val step: Token? = null,
                          val stepExpression: Expression? = null) : Statement
  data class NextStatement(val identifier: Token) : Statement
  data class GoStatement(val goType: Token, val expression: Expression) : Statement
  data class LetStatement(val identifier: Token,
                          val equals: Token,
                          val expression: Expression) : Statement

  data class InputStatement(val identifier: Token) : Statement
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
    fun isString() = string != null

    override fun toString() =
      if (isString()) string!! else expression!!.toString()
  }
}

class ParserException(message:String, lineNumber: Int, position: Int) :
  Exception("Error parsing line: $lineNumber at: $position: $message")
