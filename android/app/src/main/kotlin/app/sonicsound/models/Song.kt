package app.sonicsound.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity
class Song(
    @PrimaryKey(autoGenerate = false)
    var id: String,
    var parent: String,
    var title: String,
    var duration: Int,
    var track: Int,
    var artist: String,
    var album: String,
    var albumId: String,
    var coverArt: String,
    @Ignore
    var starred: String? = null,
) : ICardViewModel {
    constructor(
        id: String,
        parent: String,
        title: String,
        duration: Int,
        track: Int,
        artist: String,
        album: String,
        albumId: String,
        coverArt: String,
    ) : this(id, parent, title, duration, track, artist, album, albumId, coverArt, null)

    val isStarred: Boolean get() = !starred.isNullOrBlank()

    override fun firstLine(): String = title

    override fun secondLine(): String = "by $artist"

    @Ignore
    private var _image: String = ""

    override var image: String
        get() = _image
        set(value) {
            _image = value
        }
}

