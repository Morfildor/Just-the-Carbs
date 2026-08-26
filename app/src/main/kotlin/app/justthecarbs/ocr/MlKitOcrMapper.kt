package app.justthecarbs.ocr

import com.google.mlkit.vision.text.Text

/** Converts ML Kit objects at the Android boundary; no ML Kit type enters parser/domain code. */
object MlKitOcrMapper {
    fun toDocument(text: Text, imageWidth: Int, imageHeight: Int): OcrDocument {
        val elements = buildList {
            text.textBlocks.forEachIndexed { blockIndex, block ->
                block.lines.forEachIndexed { lineIndex, line ->
                    line.elements.forEach { element ->
                        val box = element.boundingBox ?: line.boundingBox ?: block.boundingBox
                        if (box != null && element.text.isNotBlank()) {
                            add(
                                OcrElement(
                                    text = element.text,
                                    box = OcrBox(box.left, box.top, box.right, box.bottom),
                                    blockId = blockIndex,
                                    lineId = lineIndex,
                                    // Retained rather than discarded (§7). NaN is mapped to null so
                                    // downstream code has one "unknown" representation and can never
                                    // accidentally compare against a NaN, which is false for every
                                    // comparison including equality. Measured as always populated on
                                    // this engine, but an engine swap must not be able to poison the
                                    // resolver silently.
                                    confidence = element.confidence.takeUnless { it.isNaN() },
                                    recognizedLanguage = element.recognizedLanguage
                                        ?.takeUnless { it.isBlank() || it == "und" },
                                ),
                            )
                        }
                    }
                }
            }
        }
        val inferredWidth = elements.maxOfOrNull { it.box.right } ?: 1
        val inferredHeight = elements.maxOfOrNull { it.box.bottom } ?: 1
        return OcrDocument(
            width = imageWidth.takeIf { it > 0 } ?: inferredWidth.coerceAtLeast(1),
            height = imageHeight.takeIf { it > 0 } ?: inferredHeight.coerceAtLeast(1),
            elements = elements,
        )
    }
}
