//! uQ's theme tokens for the vendored `uq-tokens` module (src/vendor/uq-tokens), following its
//! README's Tauri recipe. The file is `$XDG_STATE_HOME/uq/theme/tokens.json`; uqd swaps the whole
//! `theme/` directory atomically, so the watch is on its parent, and every change under `theme/`
//! emits `uq-tokens-changed`. The front end (src/uq/uqTokens.ts) validates every value before it
//! becomes CSS.
//!
//! This shell is still on Tauri 1 (`Manager::emit_all`); on Tauri 2 that becomes
//! `Emitter::emit`, and the command needs a capability permission.

use notify::{RecommendedWatcher, RecursiveMode, Watcher};
use std::path::{Path, PathBuf};
use tauri::{AppHandle, Manager};

const MAX_TOKENS_BYTES: u64 = 64 * 1024;
pub const TOKENS_CHANGED_EVENT: &str = "uq-tokens-changed";

/// `$XDG_STATE_HOME/uq` (only an absolute `XDG_STATE_HOME` counts), else `~/.local/state/uq`.
fn uq_dir() -> Option<PathBuf> {
    std::env::var_os("XDG_STATE_HOME")
        .map(PathBuf::from)
        .filter(|p| p.is_absolute())
        .or_else(|| {
            std::env::var_os("HOME")
                .map(PathBuf::from)
                .filter(|p| p.is_absolute())
                .map(|h| h.join(".local").join("state"))
        })
        .map(|s| s.join("uq"))
}

fn read_tokens(uq: &Path) -> Option<String> {
    let path = uq.join("theme").join("tokens.json");
    let meta = std::fs::metadata(&path).ok()?;
    if !meta.is_file() || meta.len() >= MAX_TOKENS_BYTES {
        return None;
    }
    std::fs::read_to_string(path).ok()
}

/// The tokens.json text, or None when uQ isn't installed or the file is unreadable.
#[tauri::command]
pub fn uq_tokens() -> Option<String> {
    read_tokens(&uq_dir()?)
}

/// Keeps the watcher alive for the app's lifetime.
struct TokensWatcher(#[allow(dead_code)] std::sync::Mutex<RecommendedWatcher>);

fn is_theme_change(theme: &Path, event: &notify::Event) -> bool {
    event.paths.iter().any(|p| p.starts_with(theme))
}

/// Watches the parent of `theme/` and emits `uq-tokens-changed`. Without `~/.local/state/uq`
/// (uQ not installed) the app keeps its own theme until it is restarted.
pub fn watch_tokens(app: &AppHandle) {
    let Some(dir) = uq_dir() else { return };
    let theme = dir.join("theme");
    let handle = app.clone();
    let watcher = notify::recommended_watcher(move |res: notify::Result<notify::Event>| {
        if res.is_ok_and(|ev| is_theme_change(&theme, &ev)) {
            let _ = handle.emit_all(TOKENS_CHANGED_EVENT, ());
        }
    });
    let Ok(mut watcher) = watcher else { return };
    if watcher.watch(&dir, RecursiveMode::NonRecursive).is_ok() {
        app.manage(TokensWatcher(std::sync::Mutex::new(watcher)));
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tempdir(name: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!("sonicsound-uq-{name}-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        dir
    }

    #[test]
    fn reads_tokens_and_rejects_oversized_or_missing() {
        let uq = tempdir("tokens");
        assert_eq!(read_tokens(&uq), None);
        std::fs::create_dir_all(uq.join("theme")).unwrap();
        std::fs::write(uq.join("theme/tokens.json"), r#"{"variant":"dark"}"#).unwrap();
        assert_eq!(read_tokens(&uq).as_deref(), Some(r#"{"variant":"dark"}"#));
        std::fs::write(uq.join("theme/tokens.json"), " ".repeat(64 * 1024)).unwrap();
        assert_eq!(read_tokens(&uq), None);
        let _ = std::fs::remove_dir_all(&uq);
    }

    #[test]
    fn theme_changes_are_paths_under_theme() {
        let theme = Path::new("/state/uq/theme");
        let ev = |p: &str| notify::Event::default().add_path(PathBuf::from(p));
        assert!(is_theme_change(theme, &ev("/state/uq/theme")));
        assert!(is_theme_change(theme, &ev("/state/uq/theme/tokens.json")));
        assert!(!is_theme_change(theme, &ev("/state/uq/theme.tmp")));
        assert!(!is_theme_change(theme, &ev("/state/uq/other")));
    }
}
