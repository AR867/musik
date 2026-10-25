package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicFormRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createPlaylist_validPayload_createsAndEmitsInFlow() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = MusicFormFirestoreRepository(firestore)

        val playlistId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createPlaylist(
                title = "Synthwave Night",
                description = "Driving beats",
                trackIds = listOf("track_01", "track_02"),
                coverTintHex = "#00E5FF"
            ).getOrThrow()
        }
        assertTrue(playlistId.isNotEmpty())

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePlaylists(uid).first { list -> list.any { it.id == playlistId } }
        }
        assertTrue(emitted.any { it.id == playlistId && it.title == "Synthwave Night" })
    }

    @Test
    fun upsertInteraction_authenticatedOwner_persistsLikesAndPlayCount() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = MusicFormFirestoreRepository(firestore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertInteraction(
                trackId = "track_neon_01",
                trackTitle = "Neon Pulse",
                artistName = "Aether Form",
                genre = "Synthwave",
                liked = true,
                disliked = false,
                playCount = 2,
                skipCount = 0,
                totalListenMs = 120000L
            ).getOrThrow()
        }

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeInteractions(uid).first { list -> list.any { it.trackId == "track_neon_01" } }
        }
        val item = emitted.first { it.trackId == "track_neon_01" }
        assertTrue(item.liked)
        assertEquals(2, item.playCount)
    }

    @Test
    fun getPlaylistById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = MusicFormFirestoreRepository(firestore)
        val playlistId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createPlaylist("Alice Private", "Secret").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = MusicFormFirestoreRepository(firestore)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getPlaylistById(aliceUid, playlistId)
        }
        assertTrue(result.isFailure)
        val fsEx = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun observePlaylists_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = MusicFormFirestoreRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observePlaylists(aliceUid).first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        assertNotNull("Expected exception for unauthenticated listener", caught)
        val fsEx = caught?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $caught", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun upsertPlaylist_and_deleteAccountData_removesAllUserSubcollectionsAndProfile() = runBlocking {
        val uid = signInTestUser(CHARLIE_EMAIL)
        val repository = MusicFormFirestoreRepository(firestore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getOrCreateProfile(
                displayName = "Charlie",
                email = CHARLIE_EMAIL
            ).getOrThrow()
            repository.upsertPlaylist(
                playlistId = "pl_synced_01",
                title = "Synced Wave",
                description = "Local playlist pushed to cloud",
                trackIds = listOf("tr_neon_horizon"),
                coverTintHex = "#7C4DFF"
            ).getOrThrow()
            repository.upsertInteraction(
                trackId = "tr_neon_horizon",
                trackTitle = "Neon Horizon",
                artistName = "Aether Pulse",
                genre = "Synthwave",
                liked = true,
                disliked = false,
                playCount = 4,
                skipCount = 0,
                totalListenMs = 95_000L
            ).getOrThrow()
        }

        val playlistsBefore = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePlaylists(uid).first { it.any { p -> p.id == "pl_synced_01" } }
        }
        assertTrue(playlistsBefore.any { it.id == "pl_synced_01" && it.title == "Synced Wave" })

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteAccountData().getOrThrow()
        }

        val playlistsAfter = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePlaylists(uid).first { it.isEmpty() }
        }
        val interactionsAfter = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeInteractions(uid).first { it.isEmpty() }
        }
        assertTrue(playlistsAfter.isEmpty())
        assertTrue(interactionsAfter.isEmpty())
    }

    private companion object {
        const val ALICE_EMAIL = "alice@musicform.test"
        const val BOB_EMAIL = "bob@musicform.test"
        const val CHARLIE_EMAIL = "charlie@musicform.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}

