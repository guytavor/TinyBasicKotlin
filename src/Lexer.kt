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

  fun hasToken() = currentIndex < program.length

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
      ':' -> return newToken(":", TokenType.COLON)
      ',' -> return newToken(",", TokenType.COMMA)
      '*' -> return newToken("*",  TokenType.ASTERISK)
      '/' -> return newToken("/",  TokenType.SLASH)
      '+' -> return newToken("+",  TokenType.PLUS)
      '-' -> return newToken("+",  TokenType.MINUS)
      '=' -> return newToken("=",  TokenType.EQ)
      '>' -> {
        return if (peek() == '=') {
          nextChar()
          newToken(">=", TokenType.GTEQ)
        } else {
          newToken(">", TokenType.GT)
        }
      }
      '<' -> {
        return when {
          peek() == '>' -> {
            nextChar()
            newToken("<>", TokenType.NOTEQ)
          }
          peek() == '=' -> {
            nextChar()
            newToken("<=", TokenType.LTEQ)
          }
          else -> {
            newToken("<", TokenType.LT)
          }
        }
      }
      in '0'..'9' -> {
        val startPos = currentIndex
        while (peek().isDigit() || peek() == '.') {
          // Get rest of number.
          nextChar()
        }

        return newToken(program.substring(startPos, currentIndex + 1),  TokenType.NUMBER)
      }

      in 'a'..'z',
      in  'A'..'Z' -> {
        // To distinguish between var, svar and keyword
        // We need to look ahead.
        val startPos = currentIndex
        while (peek().isLetterOrDigit()) {
          nextChar()
        }
        val identifier = program.substring(startPos, currentIndex + 1)
        val keywordToken = getKeywordToken(identifier)
        // If the identifier is not a keyword, it is a var.
        return if (keywordToken == null) {
          if (peek() == '$') {
            if (identifier.length != 1) {
              throw LexerException("String variable can only b 1 char long", currentLine, currentPosInLine)
            }
            nextChar()  // eat $
            newToken(identifier, TokenType.SVAR)
          } else {
            // Variable name.
            newToken(identifier, TokenType.VAR)
          }
        } else {
          // Keyword.
          if (keywordToken == TokenType.REM) {
            // Eat remark until end of line
            while (peek() != '\n') {
              nextChar()
            }
          }
          newToken(identifier,  keywordToken)
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

      else -> throw LexerException("Unexpected char: $currentChar", currentLine, currentPosInLine)
    }
  }

  private fun newToken(string: String, tokenType: TokenType) : Token =
    Token(string, tokenType, currentLine, currentPosInLine)

  private fun nextChar() {
    currentChar = program[++currentIndex]
    if (currentChar == '\n') {
      currentLine++
      currentPosInLine = 0
    } else {
      currentPosInLine++
    }

  }

  private fun peek(): Char = program[currentIndex + 1]

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
  VAR(false),   // Int variable
  SVAR(false),  // String variable
  NEWLINE(false),
  NUMBER(false),
  STRING(false),
  COMMA(isKeyword = false),
  COLON(false),

  // KEYWORDS.
  FOR(true),
  STEP(true),
  NEXT(true),
  STOP(true),
  GO(true),
  TO(true),
  SUB(true),
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
  SLASH(false),

}

class LexerException(message:String, lineNumber: Int, position: Int) :
  Exception("Syntax Error line: $lineNumber  at: $position: $message")


