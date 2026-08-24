package app.sonicsound.models

import app.sonicsound.TvLoginActivity

class Account(
    val username: String?,
    val password: String,
    val url: String,
    var type: String,
    var usePlaintext: Boolean
) {
    constructor(formdata: TvLoginActivity.FormData) : this(
        formdata.username,
        formdata.password,
        formdata.url,
        "",
        formdata.plaintext
    )
}

