package com.example.data.db

import androidx.compose.ui.geometry.Offset
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.CropQuad
import com.example.model.DocKind
import com.example.model.Document
import com.example.model.DocumentCategory
import com.example.model.FilterType
import com.example.model.ScanPage
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val kind: String,
    val subjectTag: String,
    val filePath: String?,
    val totalPages: Int,
    val lastReadPage: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val category: String,
    val isStarred: Boolean,
    val pagesJson: String
) {
    fun toDomain(): Document {
        val docKind = runCatching { DocKind.valueOf(kind) }.getOrDefault(DocKind.SCAN)
        val docCat = runCatching { DocumentCategory.valueOf(category) }.getOrDefault(DocumentCategory.ALL)
        val pages = decodePages(pagesJson)
        return Document(
            id = id,
            title = title,
            kind = docKind,
            subjectTag = subjectTag,
            filePath = filePath,
            totalPages = totalPages,
            lastReadPage = lastReadPage,
            createdAt = createdAt,
            updatedAt = updatedAt,
            category = docCat,
            isStarred = isStarred,
            pages = pages
        )
    }

    companion object {
        fun fromDomain(doc: Document): DocumentEntity {
            return DocumentEntity(
                id = doc.id,
                title = doc.title,
                kind = doc.kind.name,
                subjectTag = doc.subjectTag,
                filePath = doc.filePath,
                totalPages = doc.totalPages,
                lastReadPage = doc.lastReadPage,
                createdAt = doc.createdAt,
                updatedAt = doc.updatedAt,
                category = doc.category.name,
                isStarred = doc.isStarred,
                pagesJson = encodePages(doc.pages)
            )
        }

        private fun encodePages(pages: List<ScanPage>): String {
            val arr = JSONArray()
            pages.forEach { p ->
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("pageNumber", p.pageNumber)
                    put("originalImagePath", p.originalImagePath)
                    put("processedImagePath", p.processedImagePath)
                    put("filterType", p.filterType.name)
                    put("rotationDegrees", p.rotationDegrees)
                    put("ocrText", p.ocrText ?: "")
                    put("watermarkText", p.watermarkText ?: "")
                    put("tlx", p.cropQuad.topLeft.x.toDouble())
                    put("tly", p.cropQuad.topLeft.y.toDouble())
                    put("trx", p.cropQuad.topRight.x.toDouble())
                    put("try", p.cropQuad.topRight.y.toDouble())
                    put("brx", p.cropQuad.bottomRight.x.toDouble())
                    put("bry", p.cropQuad.bottomRight.y.toDouble())
                    put("blx", p.cropQuad.bottomLeft.x.toDouble())
                    put("bly", p.cropQuad.bottomLeft.y.toDouble())
                }
                arr.put(obj)
            }
            return arr.toString()
        }

        private fun decodePages(json: String): List<ScanPage> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val filter = runCatching {
                            FilterType.valueOf(obj.optString("filterType", FilterType.MAGIC_COLOR.name))
                        }.getOrDefault(FilterType.MAGIC_COLOR)
                        val quad = CropQuad(
                            topLeft = Offset(obj.optDouble("tlx", 0.05).toFloat(), obj.optDouble("tly", 0.05).toFloat()),
                            topRight = Offset(obj.optDouble("trx", 0.95).toFloat(), obj.optDouble("try", 0.05).toFloat()),
                            bottomRight = Offset(obj.optDouble("brx", 0.95).toFloat(), obj.optDouble("bry", 0.95).toFloat()),
                            bottomLeft = Offset(obj.optDouble("blx", 0.05).toFloat(), obj.optDouble("bly", 0.95).toFloat())
                        )
                        add(
                            ScanPage(
                                id = obj.getString("id"),
                                pageNumber = obj.optInt("pageNumber", i + 1),
                                originalImagePath = obj.getString("originalImagePath"),
                                processedImagePath = obj.getString("processedImagePath"),
                                filterType = filter,
                                rotationDegrees = obj.optInt("rotationDegrees", 0),
                                cropQuad = quad,
                                ocrText = obj.optString("ocrText").takeIf { it.isNotBlank() },
                                watermarkText = obj.optString("watermarkText").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}
