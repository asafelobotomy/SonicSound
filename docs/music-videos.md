# Music videos (YouTube)

SonicSound supports optional music-video search via the **official YouTube Data API v3** using **Google OAuth** (device-code flow for TVs).

## Setup

1. In [Google Cloud Console](https://console.cloud.google.com/), enable **YouTube Data API v3**.
2. Create an OAuth client of type **TVs and Limited Input devices**.
3. Open SonicSound **Settings** → enable music video search → paste the **OAuth client ID** (secret optional) → **Sign in with Google**.
4. On a phone/computer, open [google.com/device](https://www.google.com/device) and enter the code shown on the TV.
5. Optionally enable **Allow any YouTube channel** (default off = VEVO / Official / artist channels only).

API keys remain an optional fallback only; prefer OAuth.

## Now Playing (queue mode)

**Play Music Video** enters music-video mode for the **current play queue**:

- Each track is matched with the Data API (title must match; official channels preferred).
- Match found → YouTube plays **with audio**; Subsonic/VLC is muted and paused.
- No match → that track plays from the server with album art; mode stays on.
- When a YouTube video ends → the next queue item loads automatically.
- Pause in SonicSound pauses the YouTube video while a video is active.

## Videos page

Manual search still opens results in the official YouTube app/site (Premium users get ad-free there).

## Out of scope

Do **not** add scrapers, NewPipe/Invidious clients, or other ToS-hostile extractors.
