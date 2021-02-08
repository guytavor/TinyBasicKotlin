/**
 * Creates an AST from lexed code.
 */
class Parser(private val lexer: Lexer) {

  private var currentToken = Token("", TokenType.EOF, 0, 0)
  private var currentLineNumber: Int = 0
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
      throw ParserException("Expected $tokenType, found: ${currentToken.tokenType}")
    }
  }

  private fun nextToken() {
    currentToken = lookAhead
    lookAhead = if (lexer.hasToken()) lexer.nextToken() else Token("", TokenType.EOF, 0, 0)
  }

  private fun eatToken(tokenType: TokenType) : Token {
    matchNext(tokenType)
    return currentToken
  }

  private fun maybeEatToken(tokenType: TokenType) : Token? =
    if (peekNextToken().tokenType == tokenType) {
      eatToken(tokenType)
    } else {
      null
    }

  private fun maybeEatOneOf(tokenTypeArray: Array<TokenType>): Token? =
    if (peekNextToken().tokenType in tokenTypeArray) {
      nextToken()
      currentToken
    } else {
      null
    }

  private fun eatOneOf(tokenTypeArray: Array<TokenType>) : Token {
    nextToken()
    if (currentToken.tokenType !in tokenTypeArray) {
      throw ParserException("Expected one of ${tokenTypeArray.joinToString("," )}," +
          " found: ${currentToken.tokenType}")
    }
    return currentToken
  }

  private fun peekNextToken() : Token = lookAhead


  // Parse syntax elements, such as line, statement, expression, etc..


  private fun line() : Line {
    matchNext(TokenType.NUMBER)
    val lineNumber = currentToken.string.toInt()
    currentLineNumber = lineNumber
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
      maybeEatToken(TokenType.COLON)
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

      TokenType.DATA -> {
        return DataStatement(currentLineNumber, literalList())
      }

      TokenType.READ -> {
        return ReadStatement(variableOrDimNameList())
      }

      TokenType.RESTORE -> {

        val lineNumber = maybeEatToken(TokenType.NUMBER)
        return RestoreStatement(lineNumber)
      }

      TokenType.FOR -> {

        val identifier = variableName()
        val equals = eatToken(TokenType.EQ)
        val initialValue = expression()
        val toToken = eatToken(TokenType.TO)
        val limit = expression()
        var stepExpression: Expression? = null

        val step: Token? = maybeEatToken(TokenType.STEP)
        if (step != null) {
          stepExpression = expression()
        }

        return ForStatement(
          identifier, equals, initialValue, toToken, limit, step, stepExpression)
      }

      TokenType.NEXT -> return NextStatement(variableName())

      TokenType.PRINT -> {
        val printTerms: MutableList<PrintTerm> = mutableListOf()

        val validSeparators = arrayOf(TokenType.SEMICOLON, TokenType.COMMA)
        while (peekNextToken().tokenType !in arrayOf(TokenType.COLON, TokenType.NEWLINE)) {
          printTerms.add(
            PrintTerm(expression(), maybeEatOneOf(validSeparators))
          )
        }
        return PrintStatement(printTerms)
      }

      TokenType.IF -> return IfStatement(comparison(), eatToken(TokenType.THEN) , statement())

      TokenType.GO -> return GoStatement(goType(), expression())

      TokenType.INPUT -> {
        val inputTerms: MutableList<InputTerm> = mutableListOf()
        do {
          maybeEatToken(TokenType.COMMA)
          var prompt: String? = null
          var separator: Token? = null

          if (peekNextToken().tokenType == TokenType.STRING) {
            nextToken()
            prompt = currentToken.string
            separator = eatOneOf(arrayOf(TokenType.SEMICOLON, TokenType.COMMA))
          }
          val variable = variableOrDimName()
          inputTerms.add(InputTerm(prompt, separator, variable))
        } while (peekNextToken().tokenType == TokenType.COMMA)

        return InputStatement(inputTerms)
      }

      TokenType.LET -> {
        return LetStatement(variableOrDimName(), eatToken(TokenType.EQ), expression())
      }

      TokenType.DIM -> return DimStatement(
        variableName(),
        eatToken(TokenType.LPAR),
        expressionList(),
        eatToken(TokenType.RPAR))

      TokenType.RETURN -> return ReturnStatement()

      TokenType.STOP -> return StopStatement()

      else -> {
        throw ParserException("Unexpected: ${currentToken.string}")
      }
    }
  }

  private fun variableOrDimName(): VariableOrDimName {
    val id = variableName()
    var dimensions: List<Expression>? = null
    if (peekNextToken().tokenType == TokenType.LPAR) {
      // Dim name
      matchNext(TokenType.LPAR)
      dimensions = expressionList()
      matchNext(TokenType.RPAR)
    }
    return VariableOrDimName(id, dimensions)
  }

  private fun goType(): Token {
    nextToken()
    if (!arrayOf(TokenType.TO, TokenType.SUB).contains(currentToken.tokenType )) {
      throw ParserException("Expected either TO or SUB after GO")
    }
    return currentToken
  }

  private fun variableOrDimNameList() : List<VariableOrDimName> {
    val list = mutableListOf<VariableOrDimName>()
    // Parse first identifier.
    list.add(variableOrDimName())

    while (peekNextToken().tokenType == TokenType.COMMA) {
      nextToken()  // eat comma
      list.add(variableOrDimName())
    }

    return list
  }

  private fun literalList() : List<Value> {
    val list = mutableListOf<Value>()
    // Parse first dimension.
    list.add(literal())
    while (peekNextToken().tokenType == TokenType.COMMA) {
      nextToken()  // eat comma
      list.add(literal())
    }
    return list
  }

  private fun literal() : Value {
    nextToken()
    return when (currentToken.tokenType) {
      TokenType.STRING -> {
        Value(currentToken.string)
      }
      TokenType.NUMBER -> {
        Value(currentToken.string.toDouble())
      }
      else -> {
        throw ParserException("Literal expected but found: ${currentToken.tokenType}")
      }
    }
  }

  private fun expressionList(firstExpression: Expression? = null) : List<Expression> {
    val list = mutableListOf<Expression>()
    if (firstExpression != null) {
      // First dimension is given, add it.
      list.add(firstExpression)
    } else {
      // Parse first dimension.
      list.add(expression())
    }
    while (peekNextToken().tokenType == TokenType.COMMA) {
      nextToken()  // eat comma
      list.add(expression())
    }
    return list
  }


  // A slice has an optional start and optional finish.
  private fun slice(givenStart: Expression? = null) : Slice {
    val start = givenStart
      ?: if (peekNextToken().tokenType == TokenType.TO) {
        null
      } else {
        expression()
      }
    val to = eatToken(TokenType.TO)
    var finish: Expression? = null
    if (peekNextToken().tokenType != TokenType.RPAR) {
      // Optional right part exists.
      finish = expression()
    }
    matchNext(TokenType.RPAR)
    return Slice(start, to, finish)
  }

  private fun variableName() : Token {
    nextToken()
    if (currentToken.tokenType != TokenType.SVAR && currentToken.tokenType != TokenType.VAR) {
      throw ParserException("Scalar Variable Name expected. Found ${currentToken.string}")
    }
    return currentToken
  }

  private fun comparison() = Comparison(expression(), relop(), expression())


  private fun relop() : Token {
    nextToken()
    when (currentToken.tokenType) {
      TokenType.EQ, TokenType.GTEQ, TokenType.GT, TokenType.LT, TokenType.LTEQ, TokenType.NOTEQ ->
        return currentToken
      else -> throw ParserException("Expected operator, found ${currentToken.tokenType}")
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


  private fun primary(): Primary {
    nextToken()
    if (currentToken.tokenType != TokenType.VAR
      && currentToken.tokenType != TokenType.NUMBER
      && currentToken.tokenType != TokenType.SVAR
      && currentToken.tokenType != TokenType.STRING
      && currentToken.tokenType != TokenType.INT) {
      throw ParserException("Expecting either number, variable or string, got: ${currentToken.tokenType}")
    }

    // FIXME: can we break his to multiple parsing functions
    return when (currentToken.tokenType) {

      TokenType.SVAR, TokenType.STRING -> {
        val id = currentToken
        // After svar or sting, there's optional slice or index
        return when (peekNextToken().tokenType) {
          TokenType.LPAR -> {
            matchNext(TokenType.LPAR)
            if (peekNextToken().tokenType == TokenType.TO) {
              // Slice with no start.
              Primary(id, slice())
            } else {
              // dim reference or slice.
              val left = expression()
              when (peekNextToken().tokenType) {
                TokenType.TO -> {
                  // slice.
                  val slice = slice(left)
                  Primary(id, slice)
                }
                TokenType.COMMA, TokenType.RPAR -> {
                  // dim reference with one or more dimensions.
                  val dimensions = expressionList(left)
                  matchNext(TokenType.RPAR)
                  Primary(id, dimensions)
                }
                else -> throw ParserException("Invalid token: ${peekNextToken().tokenType}")
              }
            }
          } else -> {
            // No slice or dim, just SVAR or String.
            Primary(id)
          }
        }
      }
      TokenType.VAR, TokenType.INT -> {
        // If current is VAR, or a Function
        // the logic is same.
        // the identifier (var or function name)
        // will be followed by an expression list
        // which represents either optional dimensions list for a var
        // or mandatory arguments for a function.
        val id = currentToken
        when (peekNextToken().tokenType) {
          TokenType.LPAR -> {
            matchNext(TokenType.LPAR)
            // NumericDim reference, or function arguments
            val expressions = expressionList()
            matchNext(TokenType.RPAR)
            Primary(id, expressions)
          }
          else -> {
            // Scalar var.
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
  data class LetStatement(val variableOrDimName: VariableOrDimName,
                          val equals: Token,
                          val expression: Expression) : Statement
  data class DimStatement(val identifier: Token,
                          val lpar: Token,
                          val dimensions: List<Expression>,
                          val rpar: Token) : Statement
  data class InputStatement(val inputTerms: List<InputTerm>) : Statement
  data class IfStatement(val comparison: Comparison,
                         val then: Token,
                         val thenStatement: Statement) : Statement
  data class PrintStatement(val printTerms: List<PrintTerm>) : Statement
  data class DataStatement(val lineNumber: Int, val data: List<Value>) : Statement
  data class ReadStatement(val variableOrDimNameList: List<VariableOrDimName>) : Statement
  data class RestoreStatement(val lineNumber: Token?) : Statement
  data class InputTerm(val prompt: String?, val separator: Token?, val variableOrDimName: VariableOrDimName)
  data class PrintTerm(val expression: Expression, val separator: Token?)

  data class Comparison(val lExpression: Expression, val relop: Token, val rExpression: Expression)
  class Primary {
    val token: Token
    var slice: Slice? = null
    var expressionList: List<Expression>? = null

    constructor(id: Token) {
      this.token = id
    }
    constructor(id: Token, slice: Slice) {
      this.token = id
      this.slice = slice
    }

    constructor(id: Token, dimensions: List<Expression>) {
      this.token = id
      this.expressionList = dimensions
    }
  }
  data class Slice(val start: Expression?, val to: Token, val finish: Expression?)
  data class Unary(val op: Token?, val primary: Primary)
  data class Term(val unary: Unary, val op: Token?, val rUnary: Unary?)
  data class Expression(val term: Term, val op: Token?, val rExpression: Expression?)
  data class VariableOrDimName(val identifier: Token, val dimensions: List<Expression>?)

  inner class ParserException(message: String) :
    Exception("Error parsing line: $currentLineNumber at: ${currentToken.position}: $message")

}
