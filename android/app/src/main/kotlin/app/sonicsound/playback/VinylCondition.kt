package app.sonicsound.playback

import app.sonicsound.models.Settings

/**
 * Record wear level when [AudioProfile.VINYL] is selected.
 * Maps to a 0..1 intensity used by [VinylProcessor].
 *
 * Intensity scales surface + wear; music coloration always has a floor so even
 * Brand New is not clean digital (real decks always add wow / cartridge tilt).
 */
object VinylCondition {
    const val BRAND_NEW = "brand_new"
    const val SLIGHTLY_USED = "slightly_used"
    const val HEAVILY_USED = "heavily_used"

    val ALL: List<String> = listOf(BRAND_NEW, SLIGHTLY_USED, HEAVILY_USED)

    fun resolve(settings: Settings): String {
        val raw = settings.vinylCondition?.trim().orEmpty()
        return if (raw in ALL) raw else BRAND_NEW
    }

    fun resolve(conditionId: String?): String {
        val raw = conditionId?.trim().orEmpty()
        return if (raw in ALL) raw else BRAND_NEW
    }

    fun normalize(settings: Settings): Settings =
        settings.copy(vinylCondition = resolve(settings))

    /**
     * 0..1 wear axis.
     * Brand New raised so music coloration + light surface are audible;
     * Heavily Used remains the densest.
     */
    fun intensity(conditionId: String): Float = when (resolve(conditionId)) {
        BRAND_NEW -> 0.34f
        SLIGHTLY_USED -> 0.62f
        HEAVILY_USED -> 0.92f
        else -> 0.34f
    }

    fun skipsEnabled(conditionId: String): Boolean =
        resolve(conditionId) == HEAVILY_USED

    /** Wow/flutter always on for vinyl — even a good deck has measurable WRMS. */
    fun wowEnabled(conditionId: String): Boolean {
        resolve(conditionId)
        return true
    }
}
