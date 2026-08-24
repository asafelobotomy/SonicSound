package app.sonicsound.models

class BackendResponse<T>(val value: T) {
    val status: String = ""
    val error: String = ""
}