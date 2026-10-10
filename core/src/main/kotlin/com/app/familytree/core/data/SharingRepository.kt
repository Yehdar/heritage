package com.app.familytree.core.data

import com.app.familytree.core.model.TreeSnapshot
import kotlinx.coroutines.flow.Flow

interface SharingRepository {
    val available: Boolean
    val setupMessage: String?
    fun url(token: String): String
    fun tokenFromUrl(url: String): String
    suspend fun publish(token: String, snapshot: TreeSnapshot): TreeSnapshot
    suspend fun fetch(token: String): TreeSnapshot
    fun observe(token: String): Flow<TreeSnapshot>
    suspend fun revoke(token: String)
}

class UnconfiguredSharingRepository : SharingRepository {
    override val available = false
    override val setupMessage = "Online sharing needs Firebase setup. Add the app's Firebase configuration to enable links and live sync."
    override fun url(token: String): String = error(setupMessage)
    override fun tokenFromUrl(url: String): String = error(setupMessage)
    override suspend fun publish(token: String, snapshot: TreeSnapshot): TreeSnapshot = error(setupMessage)
    override suspend fun fetch(token: String): TreeSnapshot = error(setupMessage)
    override fun observe(token: String): Flow<TreeSnapshot> = error(setupMessage)
    override suspend fun revoke(token: String): Unit = error(setupMessage)
}
