package com.app.familytree.sharing

import android.content.Context
import android.net.Uri
import com.app.familytree.core.data.*
import com.app.familytree.core.model.TreeSnapshot
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FirebaseSharingRepository private constructor(app: FirebaseApp) : SharingRepository {
    private val auth = FirebaseAuth.getInstance(app)
    private val database = FirebaseFirestore.getInstance(app)
    private val host = "${app.options.projectId}.web.app"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    override val available = true
    override val setupMessage: String? = null
    override fun url(token: String) = "https://$host/tree/$token"
    override fun tokenFromUrl(url: String): String {
        require(url.length <= 2048) { "Sharing link is too long." }
        val uri = Uri.parse(url)
        val token = when {
            uri.scheme == "https" && uri.host == host && uri.pathSegments.size == 2 && uri.pathSegments[0] == "tree" -> uri.pathSegments[1]
            uri.scheme == "heritage" && uri.host == "tree" && uri.pathSegments.size == 1 -> uri.pathSegments[0]
            else -> error("Use a Heritage tree link from this Firebase project.")
        }
        require(token.matches(Regex("[a-f0-9]{32}")) && uri.query == null && uri.fragment == null) { "Invalid tree link." }
        return token
    }
    private suspend fun user() = auth.currentUser ?: auth.signInAnonymously().await().user ?: error("Could not sign in for sharing.")
    private fun document(token: String) = database.collection("sharedTrees").document(token)
    override suspend fun publish(token: String, snapshot: TreeSnapshot): TreeSnapshot {
        TreeIntegration.validate(snapshot)
        val uid = user().uid
        val payload = json.encodeToString(snapshot.copy(revision = 0))
        require(payload.toByteArray(Charsets.UTF_8).size <= 700_000) { "This tree is too large to share." }
        val revision = database.runTransaction { transaction ->
            val reference = document(token); val current = transaction.get(reference)
            require(!current.exists() || current.getString("ownerUid") == uid) { "You don't own this sharing link." }
            require(!current.exists() || current.getBoolean("enabled") == true) { "This sharing link has been revoked. Create a new link." }
            val oldRevision = current.getLong("revision") ?: 0
            if (current.getString("payload") == payload && current.getBoolean("enabled") == true) oldRevision
            else {
                transaction.set(reference, mapOf("ownerUid" to uid, "treeId" to snapshot.treeId, "payload" to payload, "revision" to oldRevision + 1, "enabled" to true, "updatedAt" to FieldValue.serverTimestamp()))
                oldRevision + 1
            }
        }.await()
        return snapshot.copy(revision = revision)
    }
    private fun decode(document: com.google.firebase.firestore.DocumentSnapshot): TreeSnapshot {
        require(document.exists() && document.getBoolean("enabled") == true) { "This link was revoked or no longer exists." }
        val payload = document.getString("payload") ?: error("Shared tree data is missing.")
        require(payload.length <= 700_000) { "Shared tree is too large." }
        val snapshot = json.decodeFromString<TreeSnapshot>(payload).copy(revision = document.getLong("revision") ?: error("Shared revision is missing."))
        require(snapshot.treeId == document.getString("treeId")) { "Shared tree identity is invalid." }
        TreeIntegration.validate(snapshot)
        return snapshot
    }
    override suspend fun fetch(token: String): TreeSnapshot { user(); return decode(document(token).get(Source.SERVER).await()) }
    override fun observe(token: String): Flow<TreeSnapshot> = callbackFlow {
        user()
        val registration = document(token).addSnapshotListener { document, error ->
            if (error != null) close(error)
            else if (document != null && !document.metadata.isFromCache) {
                try { trySend(decode(document)) } catch (e: Exception) { close(e) }
            }
        }
        awaitClose { registration.remove() }
    }
    override suspend fun revoke(token: String) { user(); document(token).update("enabled", false, "updatedAt", FieldValue.serverTimestamp()).await() }
    companion object {
        fun create(context: Context): SharingRepository = FirebaseApp.initializeApp(context)?.let(::FirebaseSharingRepository) ?: UnconfiguredSharingRepository()
    }
}
