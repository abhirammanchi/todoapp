package com.example.todomoji.ai

import com.example.todomoji.data.Task
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class AiService(apiKey: String?) {

    private val client: OpenAIClient =
        if (!apiKey.isNullOrBlank()) {
            OpenAIOkHttpClient.builder().apiKey(apiKey).build()
        } else {
            OpenAIOkHttpClient.fromEnv()
        }

    /** Returns Pair(orderedIds, caption) */
    suspend fun rankTasks(
        day: LocalDate,
        tasks: List<Task>
    ): Pair<List<Long>, String> = withContext(Dispatchers.IO) {
        if (tasks.isEmpty()) return@withContext emptyList<Long>() to "No tasks today."

        val items = tasks.joinToString("\n") { t ->
            """- id:${t.id}, title:"${t.title}", completed:${t.completed}"""
        }

        val prompt = """
            You are ranking a user's daily tasks for $day.
            Return STRICT JSON ONLY in this shape:
            {"ordered_task_ids":[<ids>],"caption":"<one warm sentence>"}
            Tasks:
            $items
            Respond with JUST the JSON. No code fences.
        """.trimIndent()

        // Build a user message (use the plain String overload to avoid ambiguity)
        val userParam = ChatCompletionUserMessageParam.builder()
            .content(prompt) // ← this is the key change
            .build()

        // Wrap as the union type and send
        val params = ChatCompletionCreateParams.builder()
            .model("gpt-4o-mini")
            .messages(listOf(ChatCompletionMessageParam.ofUser(userParam)))
            .build()

        val chat: ChatCompletion = client.chat().completions().create(params)
        val text = chat.choices().get(0).message().content().orElse("").trim()

        val json = JSONObject(text)
        val idArray: JSONArray = json.optJSONArray("ordered_task_ids") ?: JSONArray()
        val caption = json.optString("caption", "Your Tasks")

        val ids = buildList {
            for (i in 0 until idArray.length()) add(idArray.optLong(i))
        }
        ids to caption
    }
}
