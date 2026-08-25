# Music videos (YouTube)

SonicSound supports optional music-video search via the **official YouTube Data API v3** using **Google OAuth** (device-code flow for TVs).

## Setup

1. In [Google Cloud Console](https://console.cloud.google.com/), enable **YouTube Data API v3** and create an OAuth client for your Android package (or TV device client).
2. Open SonicSound **Settings** → enable music video search → **Sign in with Google**.
3. Pick the Google account already signed into the TV and allow YouTube access.
4. Optionally enable **Allow any YouTube channel** (default off = VEVO / Official / artist channels only).

No manual API key is required on TV when Google account authorization succeeds.

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
