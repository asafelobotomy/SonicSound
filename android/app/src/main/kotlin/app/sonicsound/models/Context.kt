package app.sonicsound.models

class Context(
    val accounts: List<Account> = emptyList(),
    val activeAccount: Account = Account(null, "", "", "", false),
)