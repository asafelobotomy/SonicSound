# Deferred desktop shell (Tauri)

This tree is kept for a future desktop milestone. It is **not** wired into
`package.json` scripts or CI. Active targets are Android (phone + TV) and the
Vite web/PWA build.

It is still on Tauri 1. `src/uq_tokens.rs` (uQ's theme for the vendored `uq-tokens`, see the main
README) uses `Manager::emit_all`; on a move to Tauri 2 that becomes `Emitter::emit`, the command
needs a capability permission, and `src/uq/uqTokens.ts` already finds `invoke` under
`window.__TAURI__.core`.
