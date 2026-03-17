package com.example.chinesepinyin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * 漢字の上にピンインをルビとして表示するカスタムView
 */
class RubyTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val chinesePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#212121")
        textSize = 56f
        isAntiAlias = true
    }

    private val pinyinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C62828")
        textSize = 28f
        isAntiAlias = true
    }

    private val spacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT
        textSize = 56f
    }

    // (漢字, ピンイン) のペアリスト
    private var charPinyinPairs: List<Pair<String, String>> = emptyList()

    // 計算済みの行データ
    private data class LineData(
        val items: List<Pair<String, String>>,
        val y: Float
    )

    private var lines: List<LineData> = emptyList()
    private var calculatedHeight = 0

    fun setCharPinyinPairs(pairs: List<Pair<String, String>>) {
        charPinyinPairs = pairs
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        calculateLines(width)
        setMeasuredDimension(width, calculatedHeight)
    }

    private fun calculateLines(availableWidth: Int) {
        lines = mutableListOf()
        val paddingH = paddingLeft + paddingRight
        val usableWidth = availableWidth - paddingH

        val lineHeight = chinesePaint.textSize + pinyinPaint.textSize + 12f
        val lineSpacing = 16f

        var currentLine = mutableListOf<Pair<String, String>>()
        var currentLineWidth = 0f
        var yPos = paddingTop + pinyinPaint.textSize + 8f

        for (pair in charPinyinPairs) {
            if (pair.first == "\n") {
                if (currentLine.isNotEmpty()) {
                    (lines as MutableList).add(LineData(currentLine.toList(), yPos))
                    yPos += lineHeight + lineSpacing
                    currentLine = mutableListOf()
                    currentLineWidth = 0f
                } else {
                    yPos += lineHeight + lineSpacing
                }
                continue
            }

            val charWidth = maxOf(
                chinesePaint.measureText(pair.first),
                pinyinPaint.measureText(pair.second) + 4f
            )

            if (currentLineWidth + charWidth > usableWidth && currentLine.isNotEmpty()) {
                (lines as MutableList).add(LineData(currentLine.toList(), yPos))
                yPos += lineHeight + lineSpacing
                currentLine = mutableListOf()
                currentLineWidth = 0f
            }

            currentLine.add(pair)
            currentLineWidth += charWidth + 4f
        }

        if (currentLine.isNotEmpty()) {
            (lines as MutableList).add(LineData(currentLine.toList(), yPos))
            yPos += lineHeight + lineSpacing
        }

        calculatedHeight = (yPos + paddingBottom).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (lineData in lines) {
            var xPos = paddingLeft.toFloat()
            val chineseY = lineData.y + chinesePaint.textSize
            val pinyinY = lineData.y

            for (pair in lineData.items) {
                val charWidth = chinesePaint.measureText(pair.first)
                val pinyinWidth = pinyinPaint.measureText(pair.second)
                val cellWidth = maxOf(charWidth, pinyinWidth + 4f)

                // ピンインを上に描画（中央揃え）
                if (pair.second.isNotEmpty()) {
                    val pinyinX = xPos + (cellWidth - pinyinWidth) / 2f
                    canvas.drawText(pair.second, pinyinX, pinyinY, pinyinPaint)
                }

                // 漢字を下に描画（中央揃え）
                val charX = xPos + (cellWidth - charWidth) / 2f
                canvas.drawText(pair.first, charX, chineseY, chinesePaint)

                xPos += cellWidth + 4f
            }
        }
    }
}
