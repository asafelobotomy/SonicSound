package app.sonicsound.models

/** YouTube Data API search result (official API only). */
class YoutubeVideo(
    val id: String,
    val title: String,
    val channel: String,
    val thumbnailUrl: String,
) : ICardViewModel {
    override fun firstLine(): String = title
    override fun secondLine(): String = channel
    override var image: String = thumbnailUrl
}
