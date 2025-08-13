package com.example.todomoji.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.todomoji.BuildConfig
import com.example.todomoji.data.Task
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Minimal Images API client (raw HTTP) for gpt-image-1.
 * Returns a content Uri to the saved PNG in cache/posters.
 */
class AiImageService(private val apiKey: String?) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMT = "application/json; charset=utf-8".toMediaType()

    fun hasKey(): Boolean = !apiKey.isNullOrBlank()

    fun generatePosterPng(
        ctx: Context,
        date: LocalDate,
        caption: String,
        tasks: List<Task>,
        size: String = "1024x1024",
        styleSeed: Long? = null
    ): Uri {
        require(!apiKey.isNullOrBlank()) { "Missing OPENAI_API_KEY" }

        val top = tasks.take(4).joinToString("; ") { t ->
            val mark = if (t.completed) "✓" else "•"
            "$mark ${t.title}"
        }

        // Tightened prompt: no text inside the image
        val vibe =
            "Design a warm, upbeat, bitmoji-like portrait of the user celebrating today’s progress. " +
                    "Include tasteful icon or small sticker references to key tasks. " +
                    "Dark, sleek UI vibe; clean composition. Do not render any words, labels, UI boxes, or text inside the image."

        val prompt = """
            Date: $date
            Caption: $caption
            Key tasks: $top
            Style: $vibe
            Output: A single square 1024×1024 PNG illustration, centered, high quality.
        """.trimIndent()

        val body = JSONObject()
            .put("model", "gpt-image-1")
            .put("prompt", if (styleSeed != null) "$prompt\nSeed:$styleSeed" else prompt)
            .put("size", size)
            .toString()
            .toRequestBody(jsonMT)

        val req = Request.Builder()
            .url("https://api.openai.com/v1/images/generations")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val bodyStr = resp.body?.string()
                    throw IllegalStateException("Images API ${resp.code}: $bodyStr")
                }
                val raw = resp.body?.string() ?: error("Empty response")
                val root = JSONObject(raw)
                val b64 = root.getJSONArray("data").getJSONObject(0).getString("b64_json")

                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val bmp: Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                val dir = File(ctx.cacheDir, "posters").apply { mkdirs() }
                val file = File(dir, "poster_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }

                return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            }
        } catch (e: SocketTimeoutException) {
            throw IllegalStateException("Image generation timed out. Please tap Regenerate.", e)
        } catch (e: IOException) {
            throw IllegalStateException("Network error: ${e.message}. Check connectivity and try again.", e)
        } catch (e: JSONException) {
            throw IllegalStateException("Unexpected response from Images API.", e)
        }
    }

    companion object {
        fun default(): AiImageService = AiImageService(BuildConfig.OPENAI_API_KEY.ifBlank { null })
    }
}
