package io.github.sanitised.st.chat.engine

import android.util.Log
import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.TavernCoreApi
import io.github.sanitised.st.api.WorldInfoEntry
import io.github.sanitised.st.chat.ChatMessage
import io.github.sanitised.st.chat.prompt.PromptBuilder
import io.github.sanitised.st.chat.prompt.TextPromptBuildResult
import io.github.sanitised.st.chat.prompt.TextPromptBuilder
import io.github.sanitised.st.chat.prompt.WorldInfoEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile

/** A single member's generated reply, ready to persist into the group JSONL. */
data class GroupReply(
    val speakerName: String,
    val text: String,
    val model: String,
    val api: String,
)

/**
 * Native group-chat reply generation.
 *
 * Mirrors [NativeChatEngine] but is decoupled from `ChatStore`/`ChatRuntimeBridge`
 * so the demo [io.github.sanitised.st.chat.GroupChatScreen] can drive it directly:
 * for a chosen speaker it loads that member's character card, assembles the prompt
 * from the **group** history via [PromptBuilder] / [TextPromptBuilder], streams the
 * reply (with a non-streaming fallback), and returns the text for the caller to
 * persist via `TavernCoreApi.saveGroupChatJsonl`.
 *
 * MVP: `generation_mode = swap` (single active character card). Joined cards
 * (merge multiple members) and per-member nudges are a later refinement.
 */
class NativeGroupGenerator(
    private val clientProvider: () -> TavernCoreApi,
) {
    @Volatile
    private var stopRequested = false

    fun requestStop() {
        stopRequested = true
    }

    /**
     * Generates [speakerAvatar]'s reply given the current group [history].
     * [onToken] receives the cumulative text as it streams in (UI mirror).
     */
    suspend fun generate(
        speakerAvatar: String,
        userName: String,
        history: List<ChatMessage>,
        authorsNote: String,
        worldInfoName: String,
        onToken: (String) -> Unit,
    ): GroupReply {
        stopRequested = false
        val client = clientProvider()
        val settings = client.getSettings()
        val mode = engineMode(settings, authorsNote)
        if (mode == NativeEngineMode.FALLBACK) {
            throw IllegalStateException("当前 API 暂不支持原生群聊生成（请使用 Chat Completion 或受支持的 Text Completion 后端）")
        }
        val character = client.getCharacter(speakerAvatar)
        val context = buildContext(client, character, history, settings, worldInfoName)

        val payload = when (mode) {
            NativeEngineMode.CHAT_COMPLETION -> PromptBuilder.build(
                character = character,
                userName = userName,
                history = history,
                settings = settings,
                personaDescription = context.personaDescription,
                worldInfoBefore = context.worldInfoBefore,
                worldInfoAfter = context.worldInfoAfter,
                authorsNote = authorsNote,
            )
            NativeEngineMode.TEXT_COMPLETION -> when (val result = TextPromptBuilder.build(
                character = character,
                userName = userName,
                history = history,
                settings = settings,
                personaDescription = context.personaDescription,
                worldInfoBefore = context.worldInfoBefore,
                worldInfoAfter = context.worldInfoAfter,
                authorsNote = authorsNote,
            )) {
                is TextPromptBuildResult.Ready -> result.payload
                is TextPromptBuildResult.Unsupported -> throw IllegalStateException(result.reason)
            }
            NativeEngineMode.FALLBACK -> error("fallback handled above")
        }

        val model = payload["model"] as? String ?: ""
        val api = if (mode == NativeEngineMode.TEXT_COMPLETION) "textgenerationwebui" else "openai"
        val source = when (mode) {
            NativeEngineMode.TEXT_COMPLETION -> payload["api_type"] as? String ?: ""
            else -> payload["chat_completion_source"] as? String ?: ""
        }
        val reply = stream(
            client = client,
            mode = mode,
            payload = payload,
            source = source,
            model = model,
            onToken = onToken,
        )
        return GroupReply(speakerName = character.name, text = reply, model = model, api = api)
    }

    private suspend fun stream(
        client: TavernCoreApi,
        mode: NativeEngineMode,
        payload: Map<String, Any?>,
        source: String,
        model: String,
        onToken: (String) -> Unit,
    ): String {
        Log.i(TAG, "group stream source=$source model=$model")
        val acc = StringBuilder()
        val streamFlow: () -> Flow<String> = {
            if (mode == NativeEngineMode.TEXT_COMPLETION) client.generateTextCompletionStream(payload)
            else client.generateChatCompletionStream(payload)
        }
        val generate: suspend () -> String = {
            if (mode == NativeEngineMode.TEXT_COMPLETION) client.generateTextCompletion(payload)
            else client.generateChatCompletion(payload)
        }
        try {
            var lastApply = 0L
            streamFlow()
                .takeWhile { !stopRequested }
                .collect { delta ->
                    acc.append(delta)
                    val now = System.currentTimeMillis()
                    if (now - lastApply >= 60) {
                        lastApply = now
                        onToken(acc.toString())
                    }
                }
            onToken(acc.toString())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (acc.isNotEmpty()) {
                onToken(acc.toString())
                throw IllegalStateException("源=$source 模型=${model.ifBlank { "(空!)" }}：${e.message}", e)
            }
            Log.w(TAG, "group stream failed, falling back to non-stream: ${e.message}")
        }
        if (acc.isEmpty() && !stopRequested) {
            val reply = try {
                generate()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                throw IllegalStateException("源=$source 模型=${model.ifBlank { "(空!)" }}：${e.message}", e)
            }
            acc.append(reply)
            onToken(acc.toString())
        }
        return acc.toString()
    }

    private suspend fun buildContext(
        client: TavernCoreApi,
        character: CharacterDetail,
        history: List<ChatMessage>,
        settings: Map<String, Any?>,
        worldInfoName: String,
    ): GroupPromptContext {
        val names = LinkedHashSet<String>()
        character.world.takeIf { it.isNotBlank() }?.let { names += it }
        worldInfoName.takeIf { it.isNotBlank() }?.let { names += it }
        val entries: List<WorldInfoEntry> = names.flatMap { name ->
            runCatching { client.getWorldInfo(name).entries }.getOrElse { emptyList() }
        }
        val wi = WorldInfoEngine.scan(
            entries = entries,
            history = history.map { it.mes },
            recursive = true,
            defaultScanDepth = settings.intValue("world_info_depth", DEFAULT_WI_SCAN_DEPTH),
        )
        val personaDescription = (settings["power_user"] as? Map<*, *>)
            ?.get("persona_description") as? String ?: ""
        return GroupPromptContext(personaDescription, wi.before, wi.after)
    }

    private fun Map<String, Any?>.intValue(key: String, default: Int): Int =
        when (val value = this[key]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }

    private data class GroupPromptContext(
        val personaDescription: String,
        val worldInfoBefore: String,
        val worldInfoAfter: String,
    )

    private companion object {
        const val TAG = "NativeGroupGenerator"
        const val DEFAULT_WI_SCAN_DEPTH = 2
    }
}

/**
 * Picks the next single speaker (member avatar) for an auto/strategy-driven turn.
 *
 * SillyTavern `group_activation_strategy`: 0=natural, 1=list, 2=manual, 3=pooled.
 * Returns null when no eligible (non-disabled) member exists, or for MANUAL (2)
 * where the caller must name the speaker explicitly.
 *
 * MVP: natural is approximated as list-order rotation; refining it to use
 * mention/recency heuristics is a later step.
 */
fun pickGroupSpeaker(
    memberAvatars: List<String>,
    disabledMembers: Set<String>,
    lastSpeakerAvatar: String?,
    activationStrategy: Int,
    allowSelfResponses: Boolean = true,
    random: () -> Double = { Math.random() },
): String? {
    val eligible = memberAvatars.filter { it !in disabledMembers }
    if (eligible.isEmpty()) return null
    return when (activationStrategy) {
        2 -> null // manual: caller must name the speaker
        3 -> {
            // pooled: random among eligible; when self-responses are off, exclude
            // the last speaker (unless they are the only eligible member).
            val pool = if (!allowSelfResponses && lastSpeakerAvatar != null && eligible.size > 1) {
                eligible.filter { it != lastSpeakerAvatar }
            } else {
                eligible
            }
            if (pool.isEmpty()) null
            else pool[(random() * pool.size).toInt().coerceIn(0, pool.lastIndex)]
        }
        else -> {
            // natural (MVP) and list: next eligible member after the last speaker.
            val startIdx = lastSpeakerAvatar?.let { eligible.indexOf(it) } ?: -1
            val next = eligible[(startIdx + 1) % eligible.size]
            // Refuse to repeat the same speaker when self-responses are disabled
            // (happens only when they are the sole eligible member).
            if (!allowSelfResponses && next == lastSpeakerAvatar) null else next
        }
    }
}
