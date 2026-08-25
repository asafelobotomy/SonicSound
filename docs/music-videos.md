# Music videos (YouTube)

SonicSound supports optional music-video search via the **official YouTube Data API v3** only.

## Setup

1. Create a [YouTube Data API v3](https://developers.google.com/youtube/v3/getting-started) key in Google Cloud Console.
2. Open **Settings** → enable music video search → paste the API key → Save.
3. Optionally enable **Allow any YouTube channel** (default off = VEVO / Official / artist channels only).

## Now Playing (queue mode)

**Play Music Video** enters music-video mode for the **current play queue** (album, playlist, radio queue, or any mix):

- Each track is matched with the Data API (title must match; official channels preferred).
- Match found → YouTube plays **with audio**; Subsonic/VLC is muted and paused.
- No match → that track plays from the server with album art; mode stays on.
- When a YouTube video ends → the next queue item loads automatically.
- Pause in SonicSound pauses the YouTube video while a video is active.

## Videos page

Manual search still opens results in the official YouTube app/site (Premium users get ad-free there).

## Out of scope

Do **not** add scrapers, NewPipe/Invidious clients, or other ToS-hostile extractors.
