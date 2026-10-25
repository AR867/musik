package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.Album
import com.example.data.model.Artist
import com.example.data.model.Track
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "listening_history")
data class ListeningHistoryEntity(
    @PrimaryKey val trackId: String,
    val trackTitle: String,
    val artistName: String,
    val genre: String,
    val liked: Boolean,
    val disliked: Boolean,
    val playCount: Int,
    val skipCount: Int,
    val totalListenMs: Long,
    val lastPlayedEpochMs: Long
)

@Entity(tableName = "local_playlists")
data class LocalPlaylistEntity(
    @PrimaryKey val playlistId: String,
    val title: String,
    val description: String,
    val trackIdsCsv: String,
    val coverTintHex: String,
    val updatedAtEpochMs: Long
) {
    fun trackIdsList(): List<String> =
        if (trackIdsCsv.isBlank()) emptyList() else trackIdsCsv.split(",").filter { it.isNotBlank() }
}

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAtEpochMs: Long
)

@Entity(tableName = "remote_cached_tracks")
data class CachedTrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val albumId: String,
    val albumTitle: String,
    val genre: String,
    val subgenre: String,
    val durationMs: Long,
    val bpm: Int,
    val energy: Float,
    val releaseYear: Int,
    val isNewRelease: Boolean,
    val popularityScore: Int,
    val coverColorPrimaryHex: String,
    val coverColorSecondaryHex: String,
    val moodTagsCsv: String,
    val licenseInfo: String,
    val coverUrl: String?,
    val streamUrl: String?,
    val downloadUrl: String?,
    val isDownloadAllowed: Boolean,
    val sourceProvider: String,
    val cachedAtEpochMs: Long
) {
    fun toDomainTrack(): Track = Track(
        id = id,
        title = title,
        artistId = artistId,
        artistName = artistName,
        albumId = albumId,
        albumTitle = albumTitle,
        genre = genre,
        subgenre = subgenre,
        durationMs = durationMs,
        bpm = bpm,
        energy = energy,
        releaseYear = releaseYear,
        isNewRelease = isNewRelease,
        popularityScore = popularityScore,
        coverColorPrimaryHex = coverColorPrimaryHex,
        coverColorSecondaryHex = coverColorSecondaryHex,
        moodTags = if (moodTagsCsv.isBlank()) emptyList() else moodTagsCsv.split(",").filter { it.isNotBlank() },
        licenseInfo = licenseInfo,
        coverUrl = coverUrl,
        streamUrl = streamUrl,
        downloadUrl = downloadUrl,
        isDownloadAllowed = isDownloadAllowed,
        isSynthesizedDemo = false,
        sourceProvider = sourceProvider
    )

    companion object {
        fun fromDomainTrack(track: Track, nowMs: Long = System.currentTimeMillis()): CachedTrackEntity =
            CachedTrackEntity(
                id = track.id,
                title = track.title,
                artistId = track.artistId,
                artistName = track.artistName,
                albumId = track.albumId,
                albumTitle = track.albumTitle,
                genre = track.genre,
                subgenre = track.subgenre,
                durationMs = track.durationMs,
                bpm = track.bpm,
                energy = track.energy,
                releaseYear = track.releaseYear,
                isNewRelease = track.isNewRelease,
                popularityScore = track.popularityScore,
                coverColorPrimaryHex = track.coverColorPrimaryHex,
                coverColorSecondaryHex = track.coverColorSecondaryHex,
                moodTagsCsv = track.moodTags.joinToString(","),
                licenseInfo = track.licenseInfo,
                coverUrl = track.coverUrl,
                streamUrl = track.streamUrl,
                downloadUrl = track.downloadUrl,
                isDownloadAllowed = track.isDownloadAllowed,
                sourceProvider = track.sourceProvider,
                cachedAtEpochMs = nowMs
            )
    }
}

@Entity(tableName = "favorite_artists")
data class FavoriteArtistEntity(
    @PrimaryKey val artistId: String,
    val name: String,
    val primaryGenre: String,
    val bio: String,
    val monthlyListeners: Int,
    val accentHex: String,
    val avatarUrl: String?,
    val sourceProvider: String,
    val addedAtEpochMs: Long
) {
    fun toDomainArtist(): Artist = Artist(
        id = artistId,
        name = name,
        primaryGenre = primaryGenre,
        bio = bio,
        monthlyListeners = monthlyListeners,
        accentHex = accentHex,
        avatarUrl = avatarUrl,
        sourceProvider = sourceProvider
    )
}

@Entity(tableName = "saved_albums")
data class SavedAlbumEntity(
    @PrimaryKey val albumId: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val genre: String,
    val releaseYear: Int,
    val coverColorPrimaryHex: String,
    val coverColorSecondaryHex: String,
    val trackIdsCsv: String,
    val description: String,
    val coverUrl: String?,
    val sourceProvider: String,
    val savedAtEpochMs: Long
) {
    fun toDomainAlbum(): Album = Album(
        id = albumId,
        title = title,
        artistId = artistId,
        artistName = artistName,
        genre = genre,
        releaseYear = releaseYear,
        coverColorPrimaryHex = coverColorPrimaryHex,
        coverColorSecondaryHex = coverColorSecondaryHex,
        trackIds = if (trackIdsCsv.isBlank()) emptyList() else trackIdsCsv.split(",").filter { it.isNotBlank() },
        description = description,
        coverUrl = coverUrl,
        sourceProvider = sourceProvider
    )
}

@Entity(tableName = "downloaded_tracks")
data class DownloadedTrackEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artistName: String,
    val albumTitle: String,
    val genre: String,
    val durationMs: Long,
    val localFilePath: String,
    val licenseInfo: String,
    val coverUrl: String?,
    val sourceProvider: String,
    val downloadedAtEpochMs: Long
)

@Dao
interface MusicFormDao {
    // --- Listening History & Likes ---
    @Query("SELECT * FROM listening_history ORDER BY lastPlayedEpochMs DESC")
    fun observeHistory(): Flow<List<ListeningHistoryEntity>>

    @Query("SELECT * FROM listening_history WHERE liked = 1 ORDER BY lastPlayedEpochMs DESC")
    fun observeLikedTracks(): Flow<List<ListeningHistoryEntity>>

    @Query("SELECT * FROM listening_history")
    suspend fun getAllHistoryOnce(): List<ListeningHistoryEntity>

    @Query("SELECT * FROM listening_history WHERE trackId = :trackId LIMIT 1")
    suspend fun getHistoryForTrack(trackId: String): ListeningHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entity: ListeningHistoryEntity)

    @Query("DELETE FROM listening_history")
    suspend fun clearAllHistory()

    // --- Local Playlists ---
    @Query("SELECT * FROM local_playlists ORDER BY updatedAtEpochMs DESC")
    fun observeLocalPlaylists(): Flow<List<LocalPlaylistEntity>>

    @Query("SELECT * FROM local_playlists")
    suspend fun getAllPlaylistsOnce(): List<LocalPlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalPlaylist(playlist: LocalPlaylistEntity)

    @Query("DELETE FROM local_playlists WHERE playlistId = :playlistId")
    suspend fun deleteLocalPlaylist(playlistId: String)

    @Query("DELETE FROM local_playlists")
    suspend fun clearAllLocalPlaylists()

    // --- Search History ---
    @Query("SELECT * FROM search_history ORDER BY searchedAtEpochMs DESC LIMIT 20")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(item: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    // --- Remote Cached Tracks ---
    @Query("SELECT * FROM remote_cached_tracks ORDER BY popularityScore DESC, cachedAtEpochMs DESC")
    fun observeCachedTracks(): Flow<List<CachedTrackEntity>>

    @Query("SELECT * FROM remote_cached_tracks ORDER BY cachedAtEpochMs DESC")
    suspend fun getAllCachedTracksOnce(): List<CachedTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedTracks(tracks: List<CachedTrackEntity>)

    @Query("DELETE FROM remote_cached_tracks")
    suspend fun clearCachedTracks()

    // --- Favorite Artists ---
    @Query("SELECT * FROM favorite_artists ORDER BY addedAtEpochMs DESC")
    fun observeFavoriteArtists(): Flow<List<FavoriteArtistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavoriteArtist(artist: FavoriteArtistEntity)

    @Query("DELETE FROM favorite_artists WHERE artistId = :artistId")
    suspend fun deleteFavoriteArtist(artistId: String)

    @Query("DELETE FROM favorite_artists")
    suspend fun clearFavoriteArtists()

    // --- Saved Albums ---
    @Query("SELECT * FROM saved_albums ORDER BY savedAtEpochMs DESC")
    fun observeSavedAlbums(): Flow<List<SavedAlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedAlbum(album: SavedAlbumEntity)

    @Query("DELETE FROM saved_albums WHERE albumId = :albumId")
    suspend fun deleteSavedAlbum(albumId: String)

    @Query("DELETE FROM saved_albums")
    suspend fun clearSavedAlbums()

    // --- Legally Downloaded Tracks ---
    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAtEpochMs DESC")
    fun observeDownloadedTracks(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks WHERE trackId = :trackId LIMIT 1")
    suspend fun getDownloadedTrack(trackId: String): DownloadedTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownloadedTrack(entity: DownloadedTrackEntity)

    @Query("DELETE FROM downloaded_tracks WHERE trackId = :trackId")
    suspend fun deleteDownloadedTrack(trackId: String)

    @Query("DELETE FROM downloaded_tracks")
    suspend fun clearDownloadedTracks()
}

@Database(
    entities = [
        ListeningHistoryEntity::class,
        LocalPlaylistEntity::class,
        SearchHistoryEntity::class,
        CachedTrackEntity::class,
        FavoriteArtistEntity::class,
        SavedAlbumEntity::class,
        DownloadedTrackEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MusicFormDatabase : RoomDatabase() {
    abstract fun musicFormDao(): MusicFormDao

    companion object {
        @Volatile
        private var INSTANCE: MusicFormDatabase? = null

        fun getInstance(context: Context): MusicFormDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MusicFormDatabase::class.java,
                    "music_form_local.db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
        }
    }
}
