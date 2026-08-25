package app.sonicsound.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity
class Artist(
    @PrimaryKey(autoGenerate = false)
    var id: String,
    var name: String,
    @Ignore
    var coverArt: String = "",
    var albumCount: Int,
    @Ignore
    var album: List<Album> = listOf(),
) : ICardViewModel {
    constructor(
        id: String,
        name: String,
        albumCount: Int,
    ) : this(id, name, "", albumCount, listOf())

    override fun firstLine(): String = name

    override fun secondLine(): String =
        if (albumCount == 1) "1 album" else "$albumCount albums"

    @Ignore
    private var _image: String = ""

    override var image: String
        get() = _image.ifBlank {
            coverArt.takeIf { it.startsWith("http", ignoreCase = true) }.orEmpty()
        }
        set(value) {
            _image = value
        }
}