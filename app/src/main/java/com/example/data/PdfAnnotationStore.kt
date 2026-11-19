package com.example.data

import android.content.Context
import com.example.model.NormPoint
import com.example.model.PdfCommentPin
import com.example.model.PdfDocumentAnnotations
import com.example.model.PdfStroke
import com.example.model.PdfStrokeType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistent JSON store for per-PDF highlights, ink strokes, sticky comments,
 * page study notes, and bookmarked pages.
 */
object PdfAnnotationStore {

    private fun annotationFile(context: Context, docId: String): File {
        val dir = File(context.filesDir, "pdf_annotations")
        if (!dir.exists()) dir.mkdirs()
        val safeId = docId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(dir, "$safeId.json")
    }

    fun load(context: Context, docId: String): PdfDocumentAnnotations {
        val file = annotationFile(context, docId)
        if (!file.exists()) return PdfDocumentAnnotations(docId = docId)
        return runCatching {
            val root = JSONObject(file.readText())
            val bookmarksArr = root.optJSONArray("bookmarks") ?: JSONArray()
            val bookmarks = buildSet {
                for (i in 0 until bookmarksArr.length()) {
                    add(bookmarksArr.getInt(i))
                }
            }

            val notesObj = root.optJSONObject("pageNotes") ?: JSONObject()
            val pageNotes = buildMap {
                val keys = notesObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val pageIdx = k.toIntOrNull()
                    val text = notesObj.optString(k, "")
                    if (pageIdx != null && text.isNotBlank()) {
                        put(pageIdx, text)
                    }
                }
            }

            val strokesArr = root.optJSONArray("strokes") ?: JSONArray()
            val strokes = buildList {
                for (i in 0 until strokesArr.length()) {
                    val obj = strokesArr.getJSONObject(i)
                    val ptsArr = obj.optJSONArray("points") ?: JSONArray()
                    val pts = buildList {
                        for (j in 0 until ptsArr.length()) {
                            val pObj = ptsArr.getJSONObject(j)
                            add(
                                NormPoint(
                                    x = pObj.optDouble("x", 0.0).toFloat(),
                                    y = pObj.optDouble("y", 0.0).toFloat()
                                )
                            )
                        }
                    }
                    val type = runCatching {
                        PdfStrokeType.valueOf(obj.optString("type", PdfStrokeType.HIGHLIGHT.name))
                    }.getOrDefault(PdfStrokeType.HIGHLIGHT)
                    add(
                        PdfStroke(
                            id = obj.getString("id"),
                            pageIndex = obj.getInt("pageIndex"),
                            type = type,
                            colorArgb = obj.getLong("colorArgb"),
                            widthRatio = obj.optDouble("widthRatio", 0.025).toFloat(),
                            points = pts
                        )
                    )
                }
            }

            val commentsArr = root.optJSONArray("comments") ?: JSONArray()
            val comments = buildList {
                for (i in 0 until commentsArr.length()) {
                    val obj = commentsArr.getJSONObject(i)
                    add(
                        PdfCommentPin(
                            id = obj.getString("id"),
                            pageIndex = obj.getInt("pageIndex"),
                            xRatio = obj.optDouble("xRatio", 0.5).toFloat(),
                            yRatio = obj.optDouble("yRatio", 0.5).toFloat(),
                            title = obj.optString("title", "Note"),
                            body = obj.optString("body", ""),
                            colorArgb = obj.optLong("colorArgb", 0xFFFFB300L),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            PdfDocumentAnnotations(
                docId = docId,
                bookmarkedPages = bookmarks,
                pageNotes = pageNotes,
                strokes = strokes,
                comments = comments
            )
        }.getOrDefault(PdfDocumentAnnotations(docId = docId))
    }

    fun save(context: Context, annotations: PdfDocumentAnnotations) {
        val file = annotationFile(context, annotations.docId)
        val root = JSONObject().apply {
            put("docId", annotations.docId)

            val bArr = JSONArray()
            annotations.bookmarkedPages.sorted().forEach { bArr.put(it) }
            put("bookmarks", bArr)

            val nObj = JSONObject()
            annotations.pageNotes.forEach { (page, note) ->
                if (note.isNotBlank()) nObj.put(page.toString(), note)
            }
            put("pageNotes", nObj)

            val sArr = JSONArray()
            annotations.strokes.forEach { s ->
                val ptsArr = JSONArray()
                s.points.forEach { pt ->
                    ptsArr.put(JSONObject().apply {
                        put("x", pt.x.toDouble())
                        put("y", pt.y.toDouble())
                    })
                }
                sArr.put(JSONObject().apply {
                    put("id", s.id)
                    put("pageIndex", s.pageIndex)
                    put("type", s.type.name)
                    put("colorArgb", s.colorArgb)
                    put("widthRatio", s.widthRatio.toDouble())
                    put("points", ptsArr)
                })
            }
            put("strokes", sArr)

            val cArr = JSONArray()
            annotations.comments.forEach { c ->
                cArr.put(JSONObject().apply {
                    put("id", c.id)
                    put("pageIndex", c.pageIndex)
                    put("xRatio", c.xRatio.toDouble())
                    put("yRatio", c.yRatio.toDouble())
                    put("title", c.title)
                    put("body", c.body)
                    put("colorArgb", c.colorArgb)
                    put("createdAt", c.createdAt)
                })
            }
            put("comments", cArr)
        }
        file.writeText(root.toString())
    }

    fun delete(context: Context, docId: String) {
        annotationFile(context, docId).delete()
    }
}
