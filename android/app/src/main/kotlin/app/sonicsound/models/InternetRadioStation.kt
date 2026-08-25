package app.sonicsound.models

import androidx.room.Ignore

class InternetRadioStationsResponse(
    val internetRadioStations: InternetRadioStationsInnerResponse?,
) : SubsonicResponse()

class InternetRadioStationsInnerResponse(
    val internetRadioStation: List<InternetRadioStation>?,
)

class InternetRadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val homePageUrl: String? = null,
) : ICardViewModel {
    override fun firstLine(): String = name

    override fun secondLine(): String = homePageUrl ?: streamUrl

    @Ignore
    private var _image: String = ""

    override var image: String
        get() = _image
        set(value) {
            _image = value
        }
}
