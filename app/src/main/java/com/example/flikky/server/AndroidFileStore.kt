package com.example.flikky.server

import android.content.Context
import com.example.flikky.server.routes.FileStore
import com.example.flikky.server.routes.PushedFile
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class AndroidFileStore(private val context: Context) : FileStore {
    private val pending = ConcurrentHashMap<String, PushedFile>()

    override fun fileDir(): File = File(context.filesDir, "transfer").apply { mkdirs() }

    override fun registerPushFromPhone(
        fileId: String,
        name: String,
        size: Long,
        mime: String,
        input: () -> java.io.InputStream,
    ) {
        pending[fileId] = PushedFile(name, size, mime, input())
    }

    override fun takePushedFile(fileId: String): PushedFile? = pending.remove(fileId)
}
