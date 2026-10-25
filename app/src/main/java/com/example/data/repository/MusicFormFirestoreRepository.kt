package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.TrackInteraction
import com.example.data.model.UserPlaylist
import com.example.data.model.UserProfile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MusicFormFirestoreRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- USER PROFILE ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val docRef = db.collection("users").document(userId)
        return docRef.snapshots()
            .map { snapshot ->
                snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is Exception && error !is CancellationException) {
                    handleFirestoreError(error, OperationType.GET, docRef.path)
                }
                throw error
            }
    }

    suspend fun getOrCreateProfile(
        displayName: String,
        email: String,
        avatarUrl: String? = null
    ): Result<UserProfile> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                existing.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(id = uid, userId = uid, displayName = displayName, email = email)
            } else {
                val newProfile = UserProfile(
                    id = uid,
                    userId = uid,
                    displayName = displayName.ifBlank { "Music Form Listener" }.take(80),
                    email = email.ifBlank { "listener@musicform.app" }.take(150),
                    avatarUrl = avatarUrl,
                    subscriptionTier = "FREE",
                    preferredGenres = listOf("Electronic", "Synthwave", "Indie"),
                    excludedArtists = emptyList(),
                    waveDiversity = 50
                )
                docRef.set(newProfile.toCreateMap()).await()
                val created = docRef.get().await()
                created.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: newProfile
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun updateProfilePreferences(
        displayName: String? = null,
        subscriptionTier: String? = null,
        preferredGenres: List<String>? = null,
        excludedArtists: List<String>? = null,
        waveDiversity: Int? = null
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val updates = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        displayName?.let { updates["displayName"] = it.take(80) }
        subscriptionTier?.let { updates["subscriptionTier"] = it }
        preferredGenres?.let { updates["preferredGenres"] = it.take(20) }
        excludedArtists?.let { updates["excludedArtists"] = it.take(50) }
        waveDiversity?.let { updates["waveDiversity"] = it.coerceIn(0, 100) }
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- PLAYLISTS ---

    fun observePlaylists(userId: String): Flow<List<UserPlaylist>> {
        val path = "users/$userId/playlists"
        return db.collection("users").document(userId).collection("playlists")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(UserPlaylist::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is Exception && error !is CancellationException) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun createPlaylist(
        title: String,
        description: String,
        trackIds: List<String> = emptyList(),
        coverTintHex: String = "#00E5FF"
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val playlistId = "pl_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("users").document(uid).collection("playlists").document(playlistId)
        val playlist = UserPlaylist(
            id = playlistId,
            userId = uid,
            title = title.ifBlank { "Мой плейлист" }.take(100),
            description = description.take(300),
            trackIds = trackIds.take(200),
            coverTintHex = coverTintHex
        )
        try {
            docRef.set(playlist.toCreateMap()).await()
            playlistId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun getUserPlaylists(userId: String = requireUserId()): Result<List<UserPlaylist>> = runCatching {
        val colRef = db.collection("users").document(userId).collection("playlists")
        try {
            val snap = colRef.get().await()
            snap.toObjects(UserPlaylist::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path)
            throw e
        }
    }

    suspend fun getPlaylistById(ownerUserId: String, playlistId: String): Result<UserPlaylist?> = runCatching {
        val docRef = db.collection("users").document(ownerUserId).collection("playlists").document(playlistId)
        try {
            val snap = docRef.get().await()
            snap.toObject(UserPlaylist::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun updatePlaylist(
        playlistId: String,
        title: String,
        description: String,
        trackIds: List<String>,
        coverTintHex: String = "#00E5FF"
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("playlists").document(playlistId)
        try {
            docRef.update(
                mapOf(
                    "title" to title.ifBlank { "Мой плейлист" }.take(100),
                    "description" to description.take(300),
                    "trackIds" to trackIds.take(200),
                    "coverTintHex" to coverTintHex.take(12),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun upsertPlaylist(
        playlistId: String,
        title: String,
        description: String,
        trackIds: List<String>,
        coverTintHex: String = "#00E5FF"
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val safeId = playlistId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        val docRef = db.collection("users").document(uid).collection("playlists").document(safeId)
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "title" to title.ifBlank { "Мой плейлист" }.take(100),
                        "description" to description.take(300),
                        "trackIds" to trackIds.take(200),
                        "coverTintHex" to coverTintHex.take(12),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val playlist = UserPlaylist(
                    id = safeId,
                    userId = uid,
                    title = title.ifBlank { "Мой плейлист" }.take(100),
                    description = description.take(300),
                    trackIds = trackIds.take(200),
                    coverTintHex = coverTintHex.take(12)
                )
                docRef.set(playlist.toCreateMap()).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("playlists").document(playlistId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- TRACK INTERACTIONS (LIKES, DISLIKES, PLAYS, SKIPS) ---

    fun observeInteractions(userId: String): Flow<List<TrackInteraction>> {
        val path = "users/$userId/interactions"
        return db.collection("users").document(userId).collection("interactions")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(TrackInteraction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is Exception && error !is CancellationException) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun upsertInteraction(
        trackId: String,
        trackTitle: String,
        artistName: String,
        genre: String,
        liked: Boolean,
        disliked: Boolean,
        playCount: Int,
        skipCount: Int,
        totalListenMs: Long
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val safeDocId = trackId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        val docRef = db.collection("users").document(uid).collection("interactions").document(safeDocId)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "trackTitle" to trackTitle.take(150),
                        "artistName" to artistName.take(150),
                        "genre" to genre.take(60),
                        "liked" to liked,
                        "disliked" to disliked,
                        "playCount" to playCount.coerceAtLeast(0),
                        "skipCount" to skipCount.coerceAtLeast(0),
                        "totalListenMs" to totalListenMs.coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val interaction = TrackInteraction(
                    id = safeDocId,
                    userId = uid,
                    trackId = safeDocId,
                    trackTitle = trackTitle,
                    artistName = artistName,
                    genre = genre,
                    liked = liked,
                    disliked = disliked,
                    playCount = playCount,
                    skipCount = skipCount,
                    totalListenMs = totalListenMs
                )
                docRef.set(interaction.toCreateMap()).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteAccountData(): Result<Unit> = runCatching {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        try {
            val playlists = userRef.collection("playlists").get().await()
            for (doc in playlists.documents) {
                doc.reference.delete().await()
            }
            val interactions = userRef.collection("interactions").get().await()
            for (doc in interactions.documents) {
                doc.reference.delete().await()
            }
            userRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userRef.path)
            throw e
        }
    }
}
