import kotlin.system.exitProcess

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
            if (keywordToken == TokenType.REM) {
              // Eat remark until end of line
              while (peek() != '\n') {
                nextChar()
              }
            }
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

