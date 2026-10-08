package com.furthersecrets.chemsearch.data

import android.content.Context
import android.content.SharedPreferences
import com.furthersecrets.chemsearch.AppLocalization
import com.furthersecrets.chemsearch.R
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coordination layer for AI description generation: prompt caching in
 * SharedPreferences and provider calls. Compound persistence lives in
 * [CompoundCacheRepository]; raw fetching lives in [CompoundDataRepository].
 */
class SearchRepository(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    private fun str(resId: Int): String = AppLocalization.string(context, prefs, resId)

    // ---- AI description cache (SharedPreferences-backed) ----

    fun loadCachedAiDescription(prompt: AiDescriptionPrompt): String? =
        prefs.getString("ai_description_${prompt.cacheKey}", null)

    fun saveCachedAiDescription(prompt: AiDescriptionPrompt, text: String) {
        prefs.edit()
            .putString("ai_description_${prompt.cacheKey}", text)
            .putString("ai_description_basis_${prompt.cacheKey}", gson.toJson(prompt.basis))
            .apply()
    }

    suspend fun fetchGeminiDescriptionBlocking(prompt: AiDescriptionPrompt, key: String, model: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = GeminiRequest(contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt.text)))))
                ApiClient.gemini.generateContent(model, key, req)
                    .candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            }.getOrNull()
        }

    suspend fun fetchChatDescriptionBlocking(provider: AiProvider, prompt: AiDescriptionPrompt, key: String, model: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = GroqRequest(model = model, messages = listOf(GroqMessage(role = "user", content = prompt.text)))
                val api = when (provider) {
                    AiProvider.GROQ -> ApiClient.groq
                    AiProvider.OPENAI -> ApiClient.openAi
                    AiProvider.OPENROUTER -> ApiClient.openRouter
                    AiProvider.MISTRAL -> ApiClient.mistral
                    AiProvider.GEMINI -> error(str(R.string.ui_error_gemini_separate_api))
                }
                api.generateContent("Bearer $key", req)
                    .choices?.firstOrNull()?.message?.content
            }.getOrNull()
        }
}
