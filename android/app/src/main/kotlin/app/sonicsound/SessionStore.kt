package app.sonicsound

import app.sonicsound.models.Account

/**
 * In-memory active [Account] for the current process. Persistence remains in
 * [KeyValueStorage]; callers that change the saved account should also [set] here.
 */
object SessionStore {
    @Volatile
    private var account: Account = Account(null, "", "", "", false)

    fun get(): Account = account

    fun set(account: Account) {
        this.account = account
    }

    fun clear() {
        account = Account(null, "", "", "", false)
    }
}
