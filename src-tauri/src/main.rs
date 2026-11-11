#![cfg_attr(
    all(not(debug_assertions), target_os = "windows"),
    windows_subsystem = "windows"
)]

mod uq_tokens;

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![uq_tokens::uq_tokens])
        .setup(|app| {
            uq_tokens::watch_tokens(&app.handle());
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
