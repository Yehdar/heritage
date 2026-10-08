package com.app.familytree

import android.util.AtomicFile
import com.app.familytree.core.data.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidStorage(directory: File) : Storage {
    private val file = AtomicFile(File(directory, "family-tree.json"))
    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) null
        else file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
    override suspend fun write(value: String) = withContext(Dispatchers.IO) {
        val stream = file.startWrite()
        try { stream.write(value.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
}
