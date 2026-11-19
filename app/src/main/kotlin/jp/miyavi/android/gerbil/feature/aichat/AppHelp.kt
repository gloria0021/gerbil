package jp.miyavi.android.gerbil.feature.aichat

import android.content.res.AssetManager
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class HelpTopic(
    val id: String,
    val title: String,
    val aliases: List<String>,
    val instructions: String,
)

internal class AppHelp(assets: AssetManager, language: String) {
    private val primary = if (language == "ja") "ja" else "en"
    private val topics = load(assets, primary)
    private val translations = load(assets, if (primary == "ja") "en" else "ja").associateBy(HelpTopic::id)
    private val index = topics.associateWith { topic ->
        val translated = translations[topic.id]
        val alternateTerms = translated?.let { it.aliases + it.title }.orEmpty()
        (topic.aliases + topic.title + alternateTerms).map(::normalize).distinct()
    }

    private fun load(assets: AssetManager, language: String): List<HelpTopic> =
        assets.open("ai-help/$language.json")
            .bufferedReader().use { Json.decodeFromString<List<HelpTopic>>(it.readText()) }

    /** AI に質問の候補を1つ作らせるときの話題（名前の由来・雑談・できることは除く）。 */
    fun randomTopic(): HelpTopic? = topics.filterNot { it.id in NON_STARTER_TOPICS }.randomOrNull()

    /** AI が作った候補が、渡した話題についての質問になっているか（動物の飼い方などに逸れていないか）。 */
    fun isStarterQuestion(question: String, topic: HelpTopic): Boolean {
        if (OFF_TOPIC_STARTER.containsMatchIn(normalize(question))) return false
        return find(question, emptyList()).firstOrNull() == topic
    }

    fun find(question: String, previous: List<HelpTopic>): List<HelpTopic> {
        val query = normalize(question)
        val words = query.split(WORD_BOUNDARY).toSet()
        val matches = index.mapNotNull { (topic, terms) ->
            val score = terms.filter { term ->
                if (term.all { it.isLetterOrDigit() && it.code < 128 }) {
                    term in words
                } else {
                    term in query
                }
            }.maxOfOrNull(String::length)
            score?.let { topic to it }
        }.sortedByDescending { it.second }.map { it.first }
        return if (matches.isNotEmpty()) matches.take(3)
        else if (FOLLOW_UP.containsMatchIn(query)) previous.take(2)
        else emptyList()
    }

    private fun normalize(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(WHITESPACE, " ").trim()

    private companion object {
        // できることは固定の候補として出すので、AI が作る候補からは外す
        val NON_STARTER_TOPICS = setOf("name-origin", "chat", "abilities", "ai-limits", "bug-report")
        val OFF_TOPIC_STARTER = Regex(
            """飼い方|飼う|飼育|ペット|ネズミ|マカロニマウス|名前の由来|\\b(pets?|mice|mouse|rodents?|hamsters?|name origin|origin of the name)\\b""",
        )
        val WHITESPACE = Regex("\\s+")
        val WORD_BOUNDARY = Regex("[^a-z0-9]+")
        val FOLLOW_UP = Regex("それ|これ|さっき|意味|違い|もとに|なぜ|どういう|\\b(it|that|those|why|difference|mean|based on)\\b")
    }
}
