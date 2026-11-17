package com.example.util

/**
 * Offline file-creation templates for the "Create New File" studio.
 * Lets users create Word (.docx), Excel (.xlsx via ExcelEngine) and every
 * common web / code / data file (html, css, js, ts, json, xml, md, txt,
 * csv, py, java, kt, cpp, c, sql, yaml) with a sensible starter template.
 * Everything is generated locally — no network, no backend.
 */
object FileTemplates {

    data class FileType(
        val ext: String,
        val label: String,
        val subtitle: String
    )

    /** Types offered in the Create-New-File dialog (docx/xlsx are handled by their own engines). */
    val CREATE_TYPES: List<FileType> = listOf(
        FileType("docx", "Word", "Formatted study notes (.docx)"),
        FileType("xlsx", "Excel", "Editable spreadsheet (.xlsx)"),
        FileType("pptx", "Slides", "PowerPoint deck (.pptx)"),
        FileType("html", "HTML", "Web page with live preview"),
        FileType("css", "CSS", "Stylesheet for web pages"),
        FileType("js", "JavaScript", "JS script (.js)"),
        FileType("ts", "TypeScript", "TS script (.ts)"),
        FileType("md", "Markdown", "Rich study notes (.md)"),
        FileType("txt", "Text", "Plain text notes (.txt)"),
        FileType("json", "JSON", "Structured data (.json)"),
        FileType("xml", "XML", "Structured markup (.xml)"),
        FileType("csv", "CSV", "Comma-separated table"),
        FileType("py", "Python", "Python script (.py)"),
        FileType("java", "Java", "Java class (.java)"),
        FileType("kt", "Kotlin", "Kotlin file (.kt)"),
        FileType("cpp", "C++", "C++ source (.cpp)"),
        FileType("c", "C", "C source (.c)"),
        FileType("sql", "SQL", "Database queries (.sql)"),
        FileType("yaml", "YAML", "Config / data (.yaml)")
    )

    fun findByExt(ext: String): FileType? = CREATE_TYPES.firstOrNull { it.ext == ext.trim('.').lowercase() }

    /** Starter file content for a given extension. */
    fun starterContent(ext: String, title: String, subjectTag: String = ""): String {
        val tagLine = if (subjectTag.isNotBlank()) "Course: $subjectTag" else title
        return when (ext.trim('.').lowercase()) {
            "html", "htm" -> """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>$title</title>
  <style>
    body { font-family: sans-serif; margin: 24px; line-height: 1.6; }
    h1 { color: #2c6e49; }
    .card { background: #f4f4f4; border-radius: 12px; padding: 16px; margin: 12px 0; }
    button { padding: 10px 18px; border: none; border-radius: 8px; background: #2c6e49; color: #fff; font-size: 16px; }
  </style>
</head>
<body>
  <h1>$title</h1>
  <p>$tagLine &mdash; created offline in Qalam. Edit this page in the code editor and tap <b>Preview</b> to render it.</p>
  <div class="card">
    <h2>Live example</h2>
    <p id="demo">Buttons, styles and scripts all work in the offline preview.</p>
    <button onclick="document.getElementById('demo').innerHTML='You clicked me! '">Try JavaScript</button>
  </div>
</body>
</html>"""

            "css" -> """/* $title — stylesheet */
:root {
  --primary: #2c6e49;
  --background: #fafbfb;
  --text: #1a2422;
}

body {
  background: var(--background);
  color: var(--text);
  font-family: sans-serif;
  line-height: 1.6;
  margin: 24px;
}

h1, h2, h3 { color: var(--primary); }

.card {
  background: #ffffff;
  border: 1px solid #e0e0e0;
  border-radius: 12px;
  padding: 16px;
  margin: 12px 0;
}

.button {
  background: var(--primary);
  color: #ffffff;
  padding: 10px 18px;
  border-radius: 8px;
  border: none;
  cursor: pointer;
}"""

            "js" -> """// $title — JavaScript
// Runs in the offline HTML preview when linked from a page, or keep as a standalone script.

function greet(name) {
  return `Hello, ${'$'}{name}! Welcome to Qalam.`;
}

const notes = ["Data Structures", "Algorithms", "Databases"];
notes.forEach((note, i) => {
  console.log(`${'$'}{i + 1}. ${'$'}{greet(note)}`);
});

// Try it:
// console.log(greet("Student"));
"""

            "ts" -> """// $title — TypeScript
interface Lecture {
  title: string;
  subject: string;
  pages: number;
}

function summarize(lecture: Lecture): string {
  return `${'$'}{lecture.title} (${'$'}{lecture.subject}) — ${'$'}{lecture.pages} pages`;
}

const lecture: Lecture = { title: "$title", subject: "${subjectTag.ifBlank { "General" }}", pages: 42 };
console.log(summarize(lecture));
"""

            "md" -> """# $title

$tagLine

## Summary

- Write your notes here in **Markdown**
- `#` headings build the outline
- **bold**, *italic* and \`code\` are supported
- Tap **Preview** to render this page offline

## Key Points

1. First key point
2. Second key point
3. Third key point

> Tip: everything stays on your device — no account, no cloud.
"""

            "txt" -> """$title
$tagLine
------------------------------------

Start typing your notes here...
"""

            "json" -> """{
  "title": "$title",
  "subject": "${subjectTag.ifBlank { "General" }}",
  "items": [
    { "id": 1, "name": "Sample item", "value": 100 },
    { "id": 2, "name": "Another item", "value": 250 }
  ],
  "createdOffline": true
}
"""

            "xml" -> """<?xml version="1.0" encoding="UTF-8"?>
<note title="$title" subject="${subjectTag.ifBlank { "General" }}">
    <item id="1">
        <name>Sample item</name>
        <value>100</value>
    </item>
    <item id="2">
        <name>Another item</name>
        <value>250</value>
    </item>
</note>
"""

            "csv" -> """"Item","Category","Value","Notes"
"Sample 1","General","100","Created in Qalam"
"Sample 2","General","200",""
"""

            "py" -> """# $title — Python
def greet(name: str) -> str:
    return f"Hello, {name}! Welcome to Qalam."


def main() -> None:
    lectures = ["Data Structures", "Algorithms", "Databases"]
    for i, lecture in enumerate(lectures, start=1):
        print(f"{i}. {greet(lecture)}")


if __name__ == "__main__":
    main()
"""

            "java" -> """public class ${sanitizeClassName(title)} {

    public static String greet(String name) {
        return "Hello, " + name + "! Welcome to Qalam.";
    }

    public static void main(String[] args) {
        String[] lectures = {"Data Structures", "Algorithms", "Databases"};
        for (int i = 0; i < lectures.length; i++) {
            System.out.println((i + 1) + ". " + greet(lectures[i]));
        }
    }
}
"""

            "kt" -> """// $title — Kotlin
fun greet(name: String): String = "Hello, ${'$'}name! Welcome to Qalam."

fun main() {
    val lectures = listOf("Data Structures", "Algorithms", "Databases")
    lectures.forEachIndexed { index, lecture ->
        println("${'$'}{index + 1}. ${'$'}{greet(lecture)}")
    }
}
"""

            "cpp" -> """// $title — C++
#include <iostream>
#include <string>
#include <vector>

std::string greet(const std::string& name) {
    return "Hello, " + name + "! Welcome to Qalam.";
}

int main() {
    std::vector<std::string> lectures = {"Data Structures", "Algorithms", "Databases"};
    for (std::size_t i = 0; i < lectures.size(); ++i) {
        std::cout << (i + 1) << ". " << greet(lectures[i]) << std::endl;
    }
    return 0;
}
"""

            "c" -> """/* $title — C */
#include <stdio.h>

int main(void) {
    const char *lectures[] = {"Data Structures", "Algorithms", "Databases"};
    for (int i = 0; i < 3; i++) {
        printf("%d. %s\n", i + 1, lectures[i]);
    }
    return 0;
}
"""

            "sql" -> """-- $title — SQL
CREATE TABLE lectures (
    id      INTEGER PRIMARY KEY,
    title   TEXT NOT NULL,
    subject TEXT,
    pages   INTEGER DEFAULT 0
);

INSERT INTO lectures (title, subject, pages) VALUES
    ('Data Structures', '${subjectTag.ifBlank { "CS" }}', 42),
    ('Algorithms', '${subjectTag.ifBlank { "CS" }}', 55);

SELECT subject, COUNT(*) AS total, SUM(pages) AS pages_total
FROM lectures
GROUP BY subject;
"""

            "yaml" -> """# $title — YAML
title: $title
subject: ${subjectTag.ifBlank { "General" }}
createdOffline: true
lectures:
  - title: Data Structures
    pages: 42
  - title: Algorithms
    pages: 55
"""

            else -> """$title

Start typing here...
"""
        }
    }

    private fun sanitizeClassName(title: String): String {
        val cleaned = title.replace(Regex("[^a-zA-Z0-9 ]"), " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar } }
        return cleaned.ifBlank { "Notes" }
    }

    /**
     * Minimal offline Markdown -> HTML renderer used by the preview screen for .md files.
     * Supports headings, bold, italic, inline code, fenced code blocks, bullet/numbered
     * lists, blockquotes, links and horizontal rules.
     */
    fun markdownToHtml(markdown: String): String {
        val out = StringBuilder()
        var inFence = false
        var inList = false
        var listType = "ul"

        fun closeList() {
            if (inList) {
                out.append("</").append(listType).append(">\n")
                inList = false
            }
        }

        fun inline(text: String): String {
            var t = escapeHtml(text)
            // links [text](url)
            t = Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)").replace(t) { m ->
                "<a href=\"${m.groupValues[2]}\">${m.groupValues[1]}</a>"
            }
            t = Regex("\\*\\*([^*]+)\\*\\*").replace(t) { m -> "<b>${m.groupValues[1]}</b>" }
            t = Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)").replace(t) { m -> "<i>${m.groupValues[1]}</i>" }
            t = Regex("`([^`]+)`").replace(t) { m -> "<code>${m.groupValues[1]}</code>" }
            return t
        }

        markdown.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.trim().startsWith("```") -> {
                    if (inFence) {
                        out.append("</code></pre>\n")
                        inFence = false
                    } else {
                        closeList()
                        out.append("<pre><code>")
                        inFence = true
                    }
                }
                inFence -> out.append(escapeHtml(raw)).append('\n')
                line.isBlank() -> { closeList(); out.append('\n') }
                line.startsWith("#") -> {
                    closeList()
                    val level = line.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    out.append("<h").append(level).append(">")
                        .append(inline(line.dropWhile { it == '#' }.trim()))
                        .append("</h").append(level).append(">\n")
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    if (!inList || listType != "ul") { closeList(); out.append("<ul>\n"); inList = true; listType = "ul" }
                    out.append("<li>").append(inline(line.drop(2).trim())).append("</li>\n")
                }
                Regex("^\\d+\\. ").containsMatchIn(line) -> {
                    if (!inList || listType != "ol") { closeList(); out.append("<ol>\n"); inList = true; listType = "ol" }
                    out.append("<li>").append(inline(line.substringAfter(". ").trim())).append("</li>\n")
                }
                line.startsWith("> ") -> {
                    closeList()
                    out.append("<blockquote>").append(inline(line.drop(2).trim())).append("</blockquote>\n")
                }
                line.trim() == "---" || line.trim() == "***" -> {
                    closeList()
                    out.append("<hr/>\n")
                }
                else -> {
                    closeList()
                    out.append("<p>").append(inline(line)).append("</p>\n")
                }
            }
        }
        closeList()
        if (inFence) out.append("</code></pre>\n")
        return out.toString()
    }

    fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /** Extensions that support the offline rendered preview (html + markdown). */
    private val PREVIEWABLE_EXTENSIONS = setOf("html", "htm", "md", "markdown")

    fun isPreviewable(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in PREVIEWABLE_EXTENSIONS
    }
}
