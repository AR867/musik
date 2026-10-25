package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val subscriptionTier: String = "FREE",
    val preferredGenres: List<String> = emptyList(),
    val excludedArtists: List<String> = emptyList(),
    val waveDiversity: Int = 50,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val base = mutableMapOf<String, Any>(
            "userId" to userId,
            "displayName" to displayName,
            "email" to email,
            "subscriptionTier" to subscriptionTier,
            "preferredGenres" to preferredGenres.take(20),
            "excludedArtists" to excludedArtists.take(50),
            "waveDiversity" to waveDiversity.coerceIn(0, 100),
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!avatarUrl.isNullOrBlank()) {
            base["avatarUrl"] = avatarUrl
        }
        return base
    }
}

@IgnoreExtraProperties
data class UserPlaylist(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val trackIds: List<String> = emptyList(),
    val coverTintHex: String = "#00E5FF",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "title" to title.take(100),
        "description" to description.take(300),
        "trackIds" to trackIds.take(200),
        "coverTintHex" to coverTintHex.take(12),
        "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class TrackInteraction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val trackId: String = "",
    val trackTitle: String = "",
    val artistName: String = "",
    val genre: String = "",
    val liked: Boolean = false,
    val disliked: Boolean = false,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val totalListenMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "trackId" to trackId.take(128),
        "trackTitle" to trackTitle.take(150),
        "artistName" to artistName.take(150),
        "genre" to genre.take(60),
        "liked" to liked,
        "disliked" to disliked,
        "playCount" to playCount.coerceAtLeast(0),
        "skipCount" to skipCount.coerceAtLeast(0),
        "totalListenMs" to totalListenMs.coerceAtLeast(0L),
        "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
