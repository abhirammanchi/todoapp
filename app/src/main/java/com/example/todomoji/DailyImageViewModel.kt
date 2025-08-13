package com.example.todomoji

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todomoji.ai.AiImageService
import com.example.todomoji.data.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import kotlin.random.Random

data class DailyImageState(
    val loading: Boolean = false,
    val uri: Uri? = null,
    val error: String? = null
)

class DailyImageViewModel : ViewModel() {
    private val _state = MutableStateFlow(DailyImageState())
    val state: StateFlow<DailyImageState> = _state

    private val images = AiImageService.default()

    /** 9A: local placeholder (kept as fallback) */
    fun generatePlaceholder(
        ctx: Context,
        date: LocalDate,
        ranked: List<Task>,
        caption: String,
        seed: Long? = null
    ) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    createLocalPlaceholderPoster(ctx, date, ranked, caption, seed)
                }
                _state.value = DailyImageState(uri = uri)
            } catch (t: Throwable) {
                _state.value = DailyImageState(error = t.message)
            }
        }
    }

    /** 9B+9C: real image generation then composite up to 6 photos (rounded tiles) onto poster. */
    fun generateWithOpenAI(
        ctx: Context,
        date: LocalDate,
        ranked: List<Task>,
        caption: String,
        seed: Long? = null,
        photoUris: List<String> = emptyList()
    ) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val finalUri = withContext(Dispatchers.IO) {
                    val basePoster: Uri =
                        if (images.hasKey()) {
                            images.generatePosterPng(
                                ctx, date, caption, ranked,
                                size = "1024x1024", styleSeed = seed
                            )
                        } else {
                            createLocalPlaceholderPoster(ctx, date, ranked, caption, seed)
                        }

                    if (photoUris.isEmpty()) return@withContext basePoster

                    val baseBmp = ctx.contentResolver.openInputStream(basePoster)!!.use {
                        BitmapFactory.decodeStream(it)!!
                    }.copy(Bitmap.Config.ARGB_8888, true)

                    val canvas = Canvas(baseBmp)

                    // --- Compose thumbnails (bottom-left, 2 columns) ---
                    val maxThumbs = 6
                    val selected = photoUris.take(maxThumbs)

                    val tile = (baseBmp.width * 0.26f).toInt()
                    val spacing = (baseBmp.width * 0.02f).toInt()
                    val corner = tile * 0.22f

                    val cols = 2
                    val startX = spacing
                    var cx = startX
                    var cy = baseBmp.height - tile - spacing
                    var col = 0

                    selected.forEach { uriStr ->
                        val bmp = loadScaledBitmap(ctx, Uri.parse(uriStr), tile) ?: return@forEach

                        drawRoundedShadow(
                            canvas = canvas,
                            x = cx.toFloat(),
                            y = cy.toFloat(),
                            size = tile,
                            radius = corner.toFloat(),
                            alpha = 0.35f,
                            radiusPx = (tile * 0.18f)
                        )
                        drawRoundedWithBorder(
                            canvas = canvas,
                            bmp = bmp,
                            x = cx.toFloat(),
                            y = cy.toFloat(),
                            size = tile,
                            radius = corner.toFloat(),
                            borderPx = (tile * 0.04f)
                        )

                        col++
                        if (col >= cols) {
                            col = 0
                            cx = startX
                            cy -= (tile + spacing)
                        } else {
                            cx += (tile + spacing)
                        }
                        bmp.recycle()
                    }

                    val dir = File(ctx.cacheDir, "posters").apply { mkdirs() }
                    val outFile = File(dir, "poster_${System.currentTimeMillis()}_mix.png")
                    FileOutputStream(outFile).use { out -> baseBmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
                    baseBmp.recycle()

                    FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", outFile)
                }
                _state.value = DailyImageState(uri = finalUri)
            } catch (t: Throwable) {
                _state.value = DailyImageState(error = t.message)
            }
        }
    }

    fun clear() { _state.value = DailyImageState() }
}

/* ---------- Helpers ---------- */

private fun createLocalPlaceholderPoster(
    ctx: Context,
    date: LocalDate,
    ranked: List<Task>,
    caption: String,
    seed: Long?
): Uri {
    val rnd = Random(seed ?: System.currentTimeMillis())
    val bmp = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)

    p.color = 0xFF121212.toInt(); c.drawRect(0f, 0f, 1024f, 1024f, p)

    val hue = rnd.nextFloat() * 360f
    p.color = Color.HSVToColor(floatArrayOf(hue, 0.7f, 0.9f))
    c.drawCircle(
        760f + rnd.nextInt(-80, 80).toFloat(),
        240f + rnd.nextInt(-80, 80).toFloat(),
        160f + rnd.nextInt(-40, 40).toFloat(),
        p
    )

    p.color = Color.WHITE
    p.textSize = 56f; c.drawText("Todomoji", 56f, 120f, p)
    p.textSize = 34f; c.drawText(date.toString(), 56f, 180f, p)
    val cap = caption.ifBlank { "Big wins incoming ✨" }
    c.drawText(cap.take(40), 56f, 230f, p)

    p.textSize = 32f
    var y = 320f
    ranked.take(3).forEach {
        val bullet = if (it.completed) "✓" else "•"
        c.drawText("$bullet ${it.title.take(30)}", 56f, y, p); y += 56f
    }

    val dir = File(ctx.cacheDir, "posters").apply { mkdirs() }
    val file = File(dir, "poster_${System.currentTimeMillis()}_ph.png")
    FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
    bmp.recycle()
    return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
}

private fun loadScaledBitmap(ctx: Context, uri: Uri, target: Int): Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
    if (o.outWidth <= 0 || o.outHeight <= 0) return null

    var sample = 1
    var w = o.outWidth
    var h = o.outHeight
    while (w / 2 >= target && h / 2 >= target) {
        w /= 2; h /= 2; sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
    return Bitmap.createScaledBitmap(bmp, target, target, true)
}

private fun drawRoundedShadow(
    canvas: Canvas,
    x: Float,
    y: Float,
    size: Int,
    radius: Float,
    alpha: Float,
    radiusPx: Float
) {
    val rect = RectF(x, y, x + size, y + size)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        this.alpha = (alpha * 255).toInt()
        setShadowLayer(radiusPx, 0f, 0f, Color.BLACK)
    }
    val saved = canvas.saveLayer(rect, null)
    val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
    canvas.drawPath(path, paint)
    canvas.restoreToCount(saved)
}

private fun drawRoundedWithBorder(
    canvas: Canvas,
    bmp: Bitmap,
    x: Float,
    y: Float,
    size: Int,
    radius: Float,
    borderPx: Float
) {
    val rect = RectF(x, y, x + size, y + size)

    val saved = canvas.saveLayer(rect, null)
    val maskPath = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
    canvas.drawPath(maskPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }
    canvas.drawBitmap(bmp, null, rect, paint)
    paint.xfermode = null
    canvas.restoreToCount(saved)

    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderPx
        color = Color.WHITE
    }
    canvas.drawRoundRect(rect, radius, radius, stroke)
}
