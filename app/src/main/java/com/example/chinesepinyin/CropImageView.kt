package com.example.chinesepinyin

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * タッチ操作でクロップ範囲を選択できるカスタムView。
 * 画像を表示しながら、ドラッグでクロップ枠を移動・各コーナーでリサイズできる。
 */
class CropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ---- ペイント ----
    private val overlayPaint = Paint().apply {
        color = Color.argb(140, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val cornerPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val gridPaint = Paint().apply {
        color = Color.argb(80, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val handlePaint = Paint().apply {
        color = Color.parseColor("#C62828")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // ---- 画像 ----
    private var bitmap: Bitmap? = null
    // 画像をView内に収めるための変換行列
    private val imageMatrix = Matrix()
    private val imageMatrixInverse = Matrix()
    // View座標での画像表示領域
    private var imageRect = RectF()

    // ---- クロップ枠（View座標） ----
    private var cropRect = RectF()
    private val CORNER_TOUCH_RADIUS = 40f
    private val EDGE_TOUCH_WIDTH = 30f
    private val MIN_CROP_SIZE = 60f

    // ---- タッチ状態 ----
    private enum class TouchMode {
        NONE, MOVE, RESIZE_TL, RESIZE_TR, RESIZE_BL, RESIZE_BR,
        RESIZE_TOP, RESIZE_BOTTOM, RESIZE_LEFT, RESIZE_RIGHT
    }
    private var touchMode = TouchMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // ---- コールバック ----
    var onCropRectChanged: ((RectF) -> Unit)? = null

    // ---- 初期化 ----
    fun setImageUri(uri: Uri) {
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        val stream = context.contentResolver.openInputStream(uri)
        val raw = BitmapFactory.decodeStream(stream, null, options)
        stream?.close()
        raw?.let { setImageBitmap(it) }
    }

    fun setImageBitmap(bmp: Bitmap) {
        bitmap = bmp
        post { initImageAndCrop() }
    }

    private fun initImageAndCrop() {
        val bmp = bitmap ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw <= 0 || vh <= 0) return

        // 画像をView内にfit-centerで収める
        val scale = min(vw / bmp.width, vh / bmp.height)
        val dx = (vw - bmp.width * scale) / 2f
        val dy = (vh - bmp.height * scale) / 2f
        imageMatrix.setScale(scale, scale)
        imageMatrix.postTranslate(dx, dy)
        imageMatrix.invert(imageMatrixInverse)

        imageRect = RectF(dx, dy, dx + bmp.width * scale, dy + bmp.height * scale)

        // クロップ枠を画像全体に初期化
        cropRect.set(imageRect)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (bitmap != null) initImageAndCrop()
    }

    // ---- 描画 ----
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return

        // 画像を描画
        canvas.drawBitmap(bmp, imageMatrix, null)

        // オーバーレイ（クロップ枠外を暗くする）
        val path = Path().apply {
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addRect(cropRect, Path.Direction.CCW)
        }
        canvas.drawPath(path, overlayPaint)

        // グリッド線（三分割）
        val gx1 = cropRect.left + cropRect.width() / 3f
        val gx2 = cropRect.left + cropRect.width() * 2f / 3f
        val gy1 = cropRect.top + cropRect.height() / 3f
        val gy2 = cropRect.top + cropRect.height() * 2f / 3f
        canvas.drawLine(gx1, cropRect.top, gx1, cropRect.bottom, gridPaint)
        canvas.drawLine(gx2, cropRect.top, gx2, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left, gy1, cropRect.right, gy1, gridPaint)
        canvas.drawLine(cropRect.left, gy2, cropRect.right, gy2, gridPaint)

        // クロップ枠の境界線
        canvas.drawRect(cropRect, borderPaint)

        // コーナーハンドル（赤い丸）
        val r = 18f
        canvas.drawCircle(cropRect.left, cropRect.top, r, handlePaint)
        canvas.drawCircle(cropRect.right, cropRect.top, r, handlePaint)
        canvas.drawCircle(cropRect.left, cropRect.bottom, r, handlePaint)
        canvas.drawCircle(cropRect.right, cropRect.bottom, r, handlePaint)

        // エッジ中央ハンドル（白い小丸）
        val er = 10f
        val cx = cropRect.centerX()
        val cy = cropRect.centerY()
        canvas.drawCircle(cx, cropRect.top, er, cornerPaint)
        canvas.drawCircle(cx, cropRect.bottom, er, cornerPaint)
        canvas.drawCircle(cropRect.left, cy, er, cornerPaint)
        canvas.drawCircle(cropRect.right, cy, er, cornerPaint)
    }

    // ---- タッチ処理 ----
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchMode = detectTouchMode(x, y)
                lastTouchX = x
                lastTouchY = y
                return touchMode != TouchMode.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchMode == TouchMode.NONE) return false
                val dx = x - lastTouchX
                val dy = y - lastTouchY
                applyTouch(dx, dy)
                lastTouchX = x
                lastTouchY = y
                clampCropToImage()
                invalidate()
                onCropRectChanged?.invoke(RectF(cropRect))
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchMode = TouchMode.NONE
                return true
            }
        }
        return false
    }

    private fun detectTouchMode(x: Float, y: Float): TouchMode {
        val cr = CORNER_TOUCH_RADIUS
        val ew = EDGE_TOUCH_WIDTH
        val cx = cropRect.centerX()
        val cy = cropRect.centerY()
        return when {
            // コーナー
            dist(x, y, cropRect.left, cropRect.top) < cr -> TouchMode.RESIZE_TL
            dist(x, y, cropRect.right, cropRect.top) < cr -> TouchMode.RESIZE_TR
            dist(x, y, cropRect.left, cropRect.bottom) < cr -> TouchMode.RESIZE_BL
            dist(x, y, cropRect.right, cropRect.bottom) < cr -> TouchMode.RESIZE_BR
            // エッジ中央
            dist(x, y, cx, cropRect.top) < cr -> TouchMode.RESIZE_TOP
            dist(x, y, cx, cropRect.bottom) < cr -> TouchMode.RESIZE_BOTTOM
            dist(x, y, cropRect.left, cy) < cr -> TouchMode.RESIZE_LEFT
            dist(x, y, cropRect.right, cy) < cr -> TouchMode.RESIZE_RIGHT
            // 枠内移動
            cropRect.contains(x, y) -> TouchMode.MOVE
            else -> TouchMode.NONE
        }
    }

    private fun applyTouch(dx: Float, dy: Float) {
        when (touchMode) {
            TouchMode.MOVE -> {
                cropRect.offset(dx, dy)
            }
            TouchMode.RESIZE_TL -> {
                cropRect.left = min(cropRect.left + dx, cropRect.right - MIN_CROP_SIZE)
                cropRect.top = min(cropRect.top + dy, cropRect.bottom - MIN_CROP_SIZE)
            }
            TouchMode.RESIZE_TR -> {
                cropRect.right = max(cropRect.right + dx, cropRect.left + MIN_CROP_SIZE)
                cropRect.top = min(cropRect.top + dy, cropRect.bottom - MIN_CROP_SIZE)
            }
            TouchMode.RESIZE_BL -> {
                cropRect.left = min(cropRect.left + dx, cropRect.right - MIN_CROP_SIZE)
                cropRect.bottom = max(cropRect.bottom + dy, cropRect.top + MIN_CROP_SIZE)
            }
            TouchMode.RESIZE_BR -> {
                cropRect.right = max(cropRect.right + dx, cropRect.left + MIN_CROP_SIZE)
                cropRect.bottom = max(cropRect.bottom + dy, cropRect.top + MIN_CROP_SIZE)
            }
            TouchMode.RESIZE_TOP -> {
                cropRect.top = min(cropRect.top + dy, cropRect.bottom - MIN_CROP_SIZE)
            }
            TouchMode.RESIZE_BOTTOM -> {
                cropRect.bottom = max(cropRect.bottom + dy, cropRect.top + MIN_CROP_SIZE)
            }
            TouchMode.RESIZE_LEFT -> {
                cropRect.left = min(cropRect.left + dx, cropRect.right - MIN_CROP_SIZE)
            }
            TouchMode.RESIZE_RIGHT -> {
                cropRect.right = max(cropRect.right + dx, cropRect.left + MIN_CROP_SIZE)
            }
            else -> {}
        }
    }

    private fun clampCropToImage() {
        val ir = imageRect
        // 枠が画像外に出ないようにクランプ
        val w = cropRect.width()
        val h = cropRect.height()
        if (cropRect.left < ir.left) cropRect.offsetTo(ir.left, cropRect.top)
        if (cropRect.top < ir.top) cropRect.offsetTo(cropRect.left, ir.top)
        if (cropRect.right > ir.right) cropRect.offsetTo(ir.right - w, cropRect.top)
        if (cropRect.bottom > ir.bottom) cropRect.offsetTo(cropRect.left, ir.bottom - h)
        // 最小サイズ保証
        cropRect.right = max(cropRect.right, cropRect.left + MIN_CROP_SIZE)
        cropRect.bottom = max(cropRect.bottom, cropRect.top + MIN_CROP_SIZE)
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /**
     * 現在のクロップ枠に対応する元画像上のRectを返す（ビットマップ座標系）
     */
    fun getCroppedBitmap(): Bitmap? {
        val bmp = bitmap ?: return null
        // View座標 → ビットマップ座標に変換
        val pts = floatArrayOf(
            cropRect.left, cropRect.top,
            cropRect.right, cropRect.bottom
        )
        imageMatrixInverse.mapPoints(pts)
        val left = max(0f, pts[0]).toInt()
        val top = max(0f, pts[1]).toInt()
        val right = min(bmp.width.toFloat(), pts[2]).toInt()
        val bottom = min(bmp.height.toFloat(), pts[3]).toInt()
        if (right <= left || bottom <= top) return null
        return Bitmap.createBitmap(bmp, left, top, right - left, bottom - top)
    }

    /**
     * クロップ枠を画像全体にリセット
     */
    fun resetCrop() {
        cropRect.set(imageRect)
        invalidate()
    }
}
