package com.example.audiobuk.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioFiles(audioFiles: List<AudioFileEntity>)

    @Update
    suspend fun updateAudioFiles(audioFiles: List<AudioFileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioFile(audioFile: AudioFileEntity)

    @Update
    suspend fun updateAudioFile(audioFile: AudioFileEntity)

    @Transaction
    @Query("SELECT * FROM playlists")
    fun getPlaylistsWithAudioFiles(): Flow<List<PlaylistWithAudioFiles>>

    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT uri FROM playlists")
    suspend fun getAllPlaylistUris(): List<String>

    @Query("SELECT * FROM audio_files WHERE playlistUri = :playlistUri")
    suspend fun getAudioFilesForPlaylist(playlistUri: String): List<AudioFileEntity>

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists WHERE uri = :uri")
    suspend fun getPlaylistByUri(uri: String): PlaylistEntity?

    @Query("DELETE FROM playlists WHERE uri = :uri")
    suspend fun deletePlaylist(uri: String)

    @Query("DELETE FROM audio_files WHERE playlistUri = :playlistUri")
    suspend fun deleteAudioFilesForPlaylist(playlistUri: String)

    @Transaction
    suspend fun deletePlaylistCompletely(uri: String) {
        deleteAudioFilesForPlaylist(uri)
        deletePlaylist(uri)
    }

    @Transaction
    suspend fun refreshLibrary(playlists: List<PlaylistEntity>, audioFiles: List<AudioFileEntity>) {
        insertPlaylists(playlists)
        insertAudioFiles(audioFiles)
    }
}

@Database(entities = [PlaylistEntity::class, AudioFileEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
