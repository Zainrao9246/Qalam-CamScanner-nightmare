package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Lightweight offline syntax highlighter for the code viewer.
 * Colorizes one line at a time (strings, numbers, comments, keywords, tags)
 * based on the file extension — zero dependencies, works fully offline,
 * fast enough for per-line use inside LazyColumn.
 */
object SyntaxHighlighter {

    enum class Lang { C_LIKE, PYTHON, HTML, XML, CSS, JSON, SQL, YAML, SHELL, MARKDOWN, PLAIN }

    fun langFor(fileName: String): Lang = when (fileName.substringAfterLast('.', "").lowercase()) {
        "java", "kt", "kts", "cpp", "cc", "cxx", "c", "h", "hpp", "js", "ts", "tsx", "jsx", "gradle" -> Lang.C_LIKE
        "py" -> Lang.PYTHON
        "html", "htm" -> Lang.HTML
        "xml" -> Lang.XML
        "css" -> Lang.CSS
        "json" -> Lang.JSON
        "sql" -> Lang.SQL
        "yaml", "yml" -> Lang.YAML
        "sh", "bat" -> Lang.SHELL
        "md", "markdown" -> Lang.MARKDOWN
        else -> Lang.PLAIN
    }

    data class Palette(
        val keyword: Color,
        val string: Color,
        val number: Color,
        val comment: Color,
        val tag: Color,
        val base: Color
    )

    fun dayPalette() = Palette(
        keyword = Color(0xFF7B1FA2),   // purple
        string = Color(0xFF2E7D32),    // green
        number = Color(0xFFB26A00),    // amber
        comment = Color(0xFF78909C),   // grey-blue
        tag = Color(0xFF1565C0),       // blue
        base = Color(0xFF1A2422)
    )

    fun nightPalette() = Palette(
        keyword = Color(0xFFCE93D8),   // light purple
        string = Color(0xFFA5D6A7),    // light green
        number = Color(0xFFFFCC80),    // light amber
        comment = Color(0xFF78909C),   // grey
        tag = Color(0xFF90CAF9),       // light blue
        base = Color(0xFFE3EFEC)
    )

    private val C_LIKE_KEYWORDS = setOf(
        "abstract", "break", "case", "catch", "class", "const", "continue", "data", "default", "do",
        "else", "enum", "extends", "false", "final", "finally", "for", "fun", "function", "if",
        "implements", "import", "in", "interface", "is", "let", "new", "null", "object", "override",
        "package", "private", "protected", "public", "return", "sealed", "static", "struct", "super",
        "switch", "suspend", "template", "this", "throw", "true", "try", "type", "typedef", "val",
        "var", "virtual", "void", "when", "while", "const", "constexpr", "include", "namespace", "using"
    )
    private val PYTHON_KEYWORDS = setOf(
        "and", "as", "assert", "async", "await", "class", "def", "del", "elif", "else", "except",
        "False", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda", "None",
        "nonlocal", "not", "or", "pass", "raise", "return", "True", "try", "while", "with", "yield"
    )
    private val SQL_KEYWORDS = setOf(
        "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE",
        "TABLE", "DROP", "ALTER", "ADD", "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "JOIN", "LEFT",
        "RIGHT", "INNER", "OUTER", "ON", "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "AS", "AND",
        "OR", "NOT", "NULL", "DISTINCT", "COUNT", "SUM", "AVG", "MIN", "MAX", "INDEX", "DEFAULT",
        "INTEGER", "TEXT", "REAL", "BLOB", "UNION", "EXISTS", "BETWEEN", "LIKE", "IN"
    )
    private val SHELL_KEYWORDS = setOf(
        "echo", "cd", "ls", "pwd", "cat", "mkdir", "rm", "cp", "mv", "touch", "chmod", "export",
        "if", "then", "else", "fi", "for", "do", "done", "while", "case", "esac", "function",
        "return", "exit", "grep", "sed", "awk", "curl", "sudo", "apt", "pip", "git", "npm", "gradle"
    )

    private val STRING_REGEX = Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`(?:\\\\.|[^`\\\\])*`")
    private val NUMBER_REGEX = Regex("(?<![\\w.])(\\d[\\d_]*(?:\\.[\\d_]+)?(?:f|L)?)(?![\\w])")
    private val WORD_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /** Returns the highlighted version of one source line. */
    fun highlightLine(line: String, lang: Lang, p: Palette): AnnotatedString = buildAnnotatedString {
        append(line)
        when (lang) {
            Lang.HTML, Lang.XML -> highlightMarkup(this, line, p)
            Lang.CSS -> highlightCss(this, line, p)
            Lang.JSON -> highlightJson(this, line, p)
            Lang.MARKDOWN -> highlightMarkdown(this, line, p)
            Lang.PLAIN -> { /* plain text, no coloring */ }
            else -> highlightCodeLine(this, line, lang, p)
        }
    }

    // ---------- generic code (C-like / Python / SQL / YAML / Shell) ----------

    private fun highlightCodeLine(
        sb: AnnotatedString.Builder,
        line: String,
        lang: Lang,
        p: Palette
    ) {
        val commentStart = when (lang) {
            Lang.C_LIKE -> findCommentStart(line, "//", "/*")
            Lang.SQL -> line.indexOf("--").takeIf { it >= 0 }
            Lang.PYTHON, Lang.YAML, Lang.SHELL -> findHashCommentStart(line)
            else -> null
        }

        if (commentStart != null && commentStart == 0) {
            sb.addStyle(SpanStyle(color = p.comment, fontStyle = FontStyle.Italic), 0, line.length)
            return
        }

        val codeEnd = commentStart ?: line.length

        // strings
        STRING_REGEX.findAll(line.take(codeEnd)).forEach { m ->
            sb.addStyle(SpanStyle(color = p.string), m.range.first, m.range.last + 1)
        }

        // numbers (skip ones inside strings)
          NUMBER_REGEX.findAll(line.take(codeEnd)).forEach { m ->
            if (!isInsideAny(m.range, line, codeEnd)) {
                sb.addStyle(SpanStyle(color = p.number), m.range.first, m.range.last + 1)
            }
        }

        // keywords (word boundary, skip strings/comments)
        val keywords = when (lang) {
            Lang.C_LIKE -> C_LIKE_KEYWORDS
            Lang.PYTHON -> PYTHON_KEYWORDS
            Lang.SQL -> SQL_KEYWORDS
            Lang.SHELL -> SHELL_KEYWORDS
            else -> emptySet()
        }
        if (keywords.isNotEmpty()) {
             WORD_REGEX.findAll(line.take(codeEnd)).forEach { m ->
                val word = m.value
                val match = (word in keywords) ||
                    (lang == Lang.SQL && word.uppercase() in SQL_KEYWORDS)
                if (match && !isInsideAny(m.range, line, codeEnd)) {
                    sb.addStyle(
                        SpanStyle(color = p.keyword, fontWeight = FontWeight.SemiBold),
                        m.range.first,
                        m.range.last + 1
                    )
                }
            }
        }

        if (commentStart != null && commentStart > 0) {
            sb.addStyle(SpanStyle(color = p.comment, fontStyle = FontStyle.Italic), commentStart, line.length)
        }
    }

    private fun findCommentStart(line: String, vararg prefixes: String): Int? {
        var best: Int? = null
        prefixes.forEach { prefix ->
            var idx = line.indexOf(prefix)
            while (idx >= 0) {
                // naive but effective: ignore occurrences inside quoted strings
                if (!isInsideAny(idx..idx + prefix.length, line, line.length)) {
                    if (best == null || idx < best!!) best = idx
                    break
                }
                idx = line.indexOf(prefix, idx + 1)
            }
        }
        return best
    }

    private fun findHashCommentStart(line: String): Int? {
        // '#' starts a comment unless inside a string
        var i = 0
        var inString = false
        var stringChar = ' '
        while (i < line.length) {
            val c = line[i]
            if (inString) {
                if (c == '\\') i++
                else if (c == stringChar) inString = false
            } else {
                if (c == '"' || c == '\'') { inString = true; stringChar = c }
                else if (c == '#') return i
            }
            i++
        }
        return null
    }

    private fun isInsideAny(range: IntRange, line: String, limit: Int): Boolean {
       STRING_REGEX.findAll(line.take(limit)).forEach { m ->
            if (range.first >= m.range.first && range.last < m.range.last + 1) return true
        }
        return false
    }

    // ---------- HTML / XML ----------

    private fun highlightMarkup(sb: AnnotatedString.Builder, line: String, p: Palette) {
        val commentStart = line.indexOf("<!--")
        if (commentStart >= 0) {
            sb.addStyle(SpanStyle(color = p.comment, fontStyle = FontStyle.Italic), commentStart, line.length)
        }
        val tagRegex = Regex("</?[a-zA-Z][a-zA-Z0-9:_-]*")
        tagRegex.findAll(line).forEach { m ->
            sb.addStyle(SpanStyle(color = p.tag, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
        }
        val attrRegex = Regex("\\b[a-zA-Z_:-]+(?==)")
        attrRegex.findAll(line).forEach { m ->
            sb.addStyle(SpanStyle(color = p.keyword), m.range.first, m.range.last + 1)
        }
        STRING_REGEX.findAll(line).forEach { m ->
            sb.addStyle(SpanStyle(color = p.string), m.range.first, m.range.last + 1)
        }
    }

    // ---------- CSS ----------

    private fun highlightCss(sb: AnnotatedString.Builder, line: String, p: Palette) {
        if (line.trimStart().startsWith("/*")) {
            sb.addStyle(SpanStyle(color = p.comment, fontStyle = FontStyle.Italic), 0, line.length)
            return
        }
        STRING_REGEX.findAll(line).forEach { m ->
            sb.addStyle(SpanStyle(color = p.string), m.range.first, m.range.last + 1)
        }
        val hexColorRegex = Regex("#[0-9a-fA-F]{3,8}\\b")
        hexColorRegex.findAll(line).forEach { m ->
            sb.addStyle(SpanStyle(color = p.number), m.range.first, m.range.last + 1)
        }
        NUMBER_REGEX.findAll(line).forEach { m ->
            if (!isInsideAny(m.range, line, line.length)) {
                sb.addStyle(SpanStyle(color = p.number), m.range.first, m.range.last + 1)
            }
        }
        val declStart = line.indexOf('{')
        val propertyRegex = Regex("\\b[a-zA-Z-]+(?=\\s*:)")
        propertyRegex.findAll(line).forEach { m ->
            if (m.range.first > declStart || declStart == -1) {
                sb.addStyle(SpanStyle(color = p.keyword), m.range.first, m.range.last + 1)
            } else {
                sb.addStyle(SpanStyle(color = p.tag, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
            }
        }
    }

    // ---------- JSON ----------

    private fun highlightJson(sb: AnnotatedString.Builder, line: String, p: Palette) {
        STRING_REGEX.findAll(line).forEach { m ->
            // key = string followed by optional space then ':'
            val after = line.substring(m.range.last + 1).trimStart()
            val style = if (after.startsWith(":")) SpanStyle(color = p.keyword) else SpanStyle(color = p.string)
            sb.addStyle(style, m.range.first, m.range.last + 1)
        }
        NUMBER_REGEX.findAll(line).forEach { m ->
            if (!isInsideAny(m.range, line, line.length)) {
                sb.addStyle(SpanStyle(color = p.number), m.range.first, m.range.last + 1)
            }
        }
        Regex("\\b(true|false|null)\\b").findAll(line).forEach { m ->
            sb.addStyle(SpanStyle(color = p.keyword, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
        }
    }

    // ---------- Markdown ----------

    private fun highlightMarkdown(sb: AnnotatedString.Builder, line: String, p: Palette) {
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("#") -> sb.addStyle(
                SpanStyle(color = p.keyword, fontWeight = FontWeight.Bold),
                0,
                line.length
            )
            trimmed.startsWith(">") -> sb.addStyle(
                SpanStyle(color = p.comment, fontStyle = FontStyle.Italic),
                0,
                line.length
            )
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> sb.addStyle(
                SpanStyle(color = p.string),
                0,
                (line.indexOfFirst { it == '-' || it == '*' } + 1).coerceAtMost(line.length)
            )
            Regex("^\\d+\\. ").containsMatchIn(trimmed) -> sb.addStyle(
                SpanStyle(color = p.number),
                0,
                line.indexOf(". ").plus(1).coerceAtMost(line.length)
            )
        }
        Regex("\\*\\*[^*]+\\*\\*").findAll(line).forEach { m ->
            sb.addStyle(SpanStyle(fontWeight = FontWeight.Bold), m.range.first, m.range.last + 1)
        }
        Regex("`[^`]+`").findAll(line).forEach { m ->
            sb.addStyle(SpanStyle(color = p.number), m.range.first, m.range.last + 1)
        }
    }
}
