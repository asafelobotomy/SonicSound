package app.sonicsound.room.database

import androidx.room.Database
import androidx.room.RoomDatabase
import app.sonicsound.models.Album
import app.sonicsound.models.Artist
import app.sonicsound.models.Song
import app.sonicsound.room.daos.AlbumDao
import app.sonicsound.room.daos.ArtistDao
import app.sonicsound.room.daos.SongDao

@Database(entities = [Song::class, Album::class, Artist::class], version = 2, exportSchema = false)
abstract class SonicSoundDatabase: RoomDatabase() {
    abstract fun songDao(): SongDao;
    abstract fun albumDao(): AlbumDao;
    abstract fun artistDao(): ArtistDao;
}