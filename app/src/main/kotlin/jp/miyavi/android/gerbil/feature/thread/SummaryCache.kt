package jp.miyavi.android.gerbil.feature.thread

import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@Serializable
internal data class SummaryCacheData(val generation: String, val results: Map<String, String> = emptyMap())

/** AI の結果をスレごとにアプリ専用キャッシュに保存する。入力本文はハッシュにする。 */
class SummaryCache(private val root: File) {
    private val mutex = Mutex()
    private val memory = object : LinkedHashMap<String, SummaryCacheData>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SummaryCacheData>?) = size > 8
    }

    internal suspend fun start(boardId: String, threadKey: Long, fresh: Boolean): SummaryCacheData = io {
        val file = file(boardId, threadKey)
        val previous = read(file)
        if (!fresh && previous != null) previous
        else SummaryCacheData(UUID.randomUUID().toString()).also { write(file, it) }
    }

    internal suspend fun save(boardId: String, threadKey: Long, generation: String, key: String, result: String) = io {
        val file = file(boardId, threadKey)
        val current = read(file)
        if (current?.generation == generation) {
            val results = (current.results + (key to result)).entries.toList().takeLast(MAX_RESULTS).associate { it.toPair() }
            write(file, current.copy(results = results))
        }
    }

    internal suspend fun hasVerdicts(boardId: String, threadKey: Long): Boolean = io {
        !read(file(boardId, threadKey))?.results.isNullOrEmpty()
    }

    internal suspend fun saveVerdicts(
        boardId: String,
        threadKey: Long,
        generation: String,
        verdicts: Map<String, String>,
    ) = io {
        val file = file(boardId, threadKey)
        val current = read(file)
        if (current?.generation == generation) write(file, current.copy(results = current.results + verdicts))
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }
    private fun file(boardId: String, threadKey: Long) = AtomicFile(File(root, "${digest(listOf(boardId, threadKey.toString()))}.json"))
    private fun read(file: AtomicFile): SummaryCacheData? = try {
        memory[file.baseFile.name] ?: Json.decodeFromString<SummaryCacheData>(file.readFully().toString(Charsets.UTF_8))
            .also { memory[file.baseFile.name] = it }
    } catch (_: Exception) { null }

    private fun write(file: AtomicFile, data: SummaryCacheData) {
        memory[file.baseFile.name] = data
        try {
            root.mkdirs()
            val out = file.startWrite()
            try {
                out.write(Json.encodeToString(data).toByteArray(Charsets.UTF_8))
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
                throw e
            }
        } catch (_: Exception) {
            // キャッシュを使えなくても要約処理自体は続ける。
        }
    }

    companion object {
        private const val MAX_RESULTS = 2048

        internal fun key(model: String, merging: Boolean, texts: List<String>): String =
            // 指示文や出力枠を変えたら版を上げ、古い指示で作った部分要約を使い回さない
            digest(listOf("summary-v16", model, merging.toString()) + texts)

        internal fun verdictKey(number: Int, prompt: String): String = digest(listOf("mute-v1", number.toString(), prompt))

        private fun digest(parts: List<String>): String {
            val md = MessageDigest.getInstance("SHA-256")
            for (part in parts) {
                val bytes = part.toByteArray(Charsets.UTF_8)
                md.update(byteArrayOf((bytes.size ushr 24).toByte(), (bytes.size ushr 16).toByte(), (bytes.size ushr 8).toByte(), bytes.size.toByte()))
                md.update(bytes)
            }
            return md.digest().toHexString()
        }
    }
}
