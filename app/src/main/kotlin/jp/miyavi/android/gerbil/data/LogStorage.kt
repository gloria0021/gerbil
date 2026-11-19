package jp.miyavi.android.gerbil.data

import java.io.File

/** スレ本文（dat の生バイト列）を端末に保存する。追記と置き換えのみ。 */
class LogStorage(private val root: File) {
    private fun file(boardId: String, threadKey: Long): File {
        val dir = File(root, boardId.replace(':', '/'))
        return File(dir, "$threadKey.dat")
    }

    fun read(boardId: String, threadKey: Long): ByteArray? =
        file(boardId, threadKey).takeIf { it.exists() }?.readBytes()

    fun replace(boardId: String, threadKey: Long, bytes: ByteArray) {
        val f = file(boardId, threadKey)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }

    fun append(boardId: String, threadKey: Long, bytes: ByteArray) {
        val f = file(boardId, threadKey)
        f.parentFile?.mkdirs()
        f.appendBytes(bytes)
    }

    fun size(boardId: String, threadKey: Long): Long = file(boardId, threadKey).length()

    fun deleteAll() {
        root.deleteRecursively()
    }
}
