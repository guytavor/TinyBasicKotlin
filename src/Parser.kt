/**
 * Creates an AST from lexed code.
 */
class Parser(private val lexer: Lexer) {

  private var currentToken = Token("", TokenType.EOF, 0, 0)
  private var lookAhead = Token("", TokenType.EOF, 0, 0)

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
        val equals = eatToken(TokenType.EQ)
        val initialValue = expression()
        val toToken = eatToken(TokenType.TO)
        val limit = expression()
        var step: Token? = null
        var stepExpression: Expression? = null

        if (peekNextToken().tokenType == TokenType.STEP) {
            step = eatToken(TokenType.STEP)
            stepExpression = expression()
        }

        return ForStatement(
          identifier, equals, initialValue, toToken, limit, step, stepExpression)
      }

      TokenType.NEXT -> return NextStatement(identifier())

      TokenType.PRINT -> return PrintStatement(expression())

      TokenType.IF -> return IfStatement(comparison(), eatToken(TokenType.THEN) , statement())

      TokenType.GO -> return GoStatement(goType(), expression())

      TokenType.INPUT -> return InputStatement(identifier())

      TokenType.LET -> return LetStatement(identifier(), eatToken(TokenType.EQ), expression())

      TokenType.DIM -> return DimStatement(identifier(), eatToken(TokenType.LPAR), dimensions(), eatToken(TokenType.RPAR))

      TokenType.RETURN -> return ReturnStatement()

      TokenType.STOP -> return StopStatement()

      else -> {
        throw ParserException("Unexpected: ${currentToken.string}", currentToken.line, currentToken.position)
      }
    }
  }

  private fun eatToken(tokenType: TokenType) : Token {
    matchNext(tokenType)
    return currentToken
  }

  private fun goType(): Token {
    nextToken()
    if (!arrayOf(TokenType.TO, TokenType.SUB).contains(currentToken.tokenType )) {
      throw ParserException("Expected either TO or SUB after GO", currentToken.line, currentToken.position)
    }
    return currentToken
  }

  private fun dimensions(firstDimension: Expression? = null) : List<Expression> {
    val dimensions = mutableListOf<Expression>()
    if (firstDimension != null) {
      // First dimension is given, add it.
      dimensions.add(firstDimension)
    } else {
      // Parse first dimension.
      dimensions.add(expression())
    }
    while (peekNextToken().tokenType == TokenType.COMMA) {
      nextToken()  // eat comma
      dimensions.add(expression())
    }
    return dimensions
  }

  private fun identifier() : Token {
    nextToken()
    if (currentToken.tokenType != TokenType.SVAR && currentToken.tokenType != TokenType.VAR) {
      throw ParserException("Identifier expected. Found ${currentToken.string}", currentToken.line,
        currentToken.position)
    }
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
    val rExpression: Expression?

    return when (peekNextToken().tokenType) {
      TokenType.PLUS, TokenType.MINUS -> {
        nextToken()
        op = currentToken
        rExpression = expression()
        Expression(term, op, rExpression)
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

    if (peekNextToken().tokenType == TokenType.PLUS || peekNextToken().tokenType == TokenType.MINUS) {
      nextToken()
      op = currentToken
    }
    val primary = primary()
    return Unary(op, primary)
  }


  private fun primary() : Primary {
    nextToken()
    if (currentToken.tokenType != TokenType.VAR
      && currentToken.tokenType != TokenType.NUMBER
      && currentToken.tokenType != TokenType.SVAR
      && currentToken.tokenType != TokenType.STRING) {
      throw ParserException("Expecting either number, variable or string, got: ${currentToken.tokenType}",
        currentToken.line, currentToken.position)
    }

    // FIXME: can we break his to multiple parsing functions
    return when (currentToken.tokenType) {

      TokenType.SVAR, TokenType.STRING -> {
        val id = currentToken
        // After svar, there's optional slice or index
        return when (peekNextToken().tokenType) {
          TokenType.LPAR -> {
            matchNext(TokenType.LPAR)
            var left: Expression? = null;
            if (peekNextToken().tokenType != TokenType.TO) {
              // Slice without start part.
              left = expression()
            }

            when (peekNextToken().tokenType) {
              TokenType.TO -> {
                // slice.
                val to = eatToken(TokenType.TO)
                var right: Expression? = null
                if (peekNextToken().tokenType != TokenType.RPAR) {
                  // No right part.
                  right = expression()
                }
                matchNext(TokenType.RPAR)
                Primary(id, Slice(left, to, right))
              }
              TokenType.COMMA -> {
                // dim reference.
                matchNext(TokenType.COMMA)
                val dimensions = dimensions(left)
                matchNext(TokenType.RPAR)
                Primary(id, null, dimensions)
              }
              else -> throw ParserException("Invalid token: ${peekNextToken().tokenType}", currentToken.line, currentToken.position)
            }
          }
          else -> {
            // SVAR
            Primary(id)
          }
        }
      }
      TokenType.VAR -> {
        val id = currentToken
        when (peekNextToken().tokenType) {
          TokenType.LPAR -> {
            matchNext(TokenType.LPAR)
            // NumericDim reference.
            val dimensions = dimensions()
            matchNext(TokenType.RPAR)
            Primary(id, null, dimensions)
          }
          else -> {
            // Var
            Primary(id)
          }
        }
      }
      else -> {
        return Primary(currentToken)
      }
    }
  }

  /**
   * Abstract syntax tree represents a program in an abstract object form.
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
  data class DimStatement(val identifier: Token,
                          val lpar: Token,
                          val dimensions: List<Expression>,
                          val rpar: Token) : Statement
  data class InputStatement(val identifier: Token) : Statement
  data class IfStatement(val comparison: Comparison,
                         val then: Token,
                         val thenStatement: Statement) : Statement
  data class PrintStatement(val expression: Expression) : Statement


  data class Comparison(val lExpression: Expression, val relop: Token, val rExpression: Expression)
  data class Primary(val token: Token, val slice: Slice? = null, val dimensions: List<Expression>? = null)
  data class Slice(val start: Expression?, val to: Token, val finish: Expression?)
  data class Unary(val op: Token?, val primary: Primary)
  data class Term(val unary: Unary, val op: Token?, val rUnary: Unary?)
  data class Expression(val term: Term, val op: Token?, val rExpression: Expression?)
}

class ParserException(message:String, lineNumber: Int, position: Int) :
  Exception("Error parsing line: $lineNumber at: $position: $message")
