export type YtCandidate = {
    id: string;
    title: string;
    channel: string;
    thumb?: string;
};

function score(
    video: YtCandidate,
    artist: string,
    title: string,
    allowAnyChannel: boolean
): number {
    const vt = video.title.toLowerCase();
    const ch = video.channel.toLowerCase();
    const a = artist.toLowerCase().trim();
    const t = title.toLowerCase().trim();
    if (!t || !vt.includes(t)) return 0;

    const junk = [
        "lyric",
        "lyrics",
        "audio only",
        "cover",
        "karaoke",
        "reaction",
        "fan made",
        "fanmade",
        "nightcore",
        "slowed",
        "sped up",
        "instrumental",
    ];
    if (!allowAnyChannel && junk.some((j) => vt.includes(j))) return 0;

    const officialTitle = [
        "official music video",
        "official video",
        "official mv",
    ].some((x) => vt.includes(x));
    const vevo = ch.endsWith("vevo") || ch.includes("vevo");
    const artistChannel =
        !!a &&
        (ch === a ||
            ch.startsWith(`${a} `) ||
            ch.includes(`${a} -`) ||
            ch.includes(`${a} official`) ||
            ch.includes(`official ${a}`));
    const officialInChannel = ch.includes("official");
    const trusted = vevo || officialTitle || artistChannel || officialInChannel;
    if (!allowAnyChannel && !trusted) return 0;

    let s = 40;
    if (a && (vt.includes(a) || ch.includes(a))) s += 25;
    if (officialTitle) s += 35;
    if (vevo) s += 40;
    if (artistChannel) s += 30;
    if (officialInChannel) s += 15;
    if (ch.includes("- topic")) s -= 40;
    return s;
}

export function pickMusicVideo(
    candidates: YtCandidate[],
    artist: string,
    title: string,
    allowAnyChannel: boolean
): YtCandidate | null {
    const best = candidates
        .map((c) => ({ c, s: score(c, artist, title, allowAnyChannel) }))
        .filter((x) => x.s > 0)
        .sort((a, b) => b.s - a.s)[0];
    return best?.c ?? null;
}

export async function searchMusicVideo(
    apiKey: string,
    artist: string,
    title: string,
    allowAnyChannel: boolean
): Promise<YtCandidate | null> {
    const queries = [
        `${artist} ${title} Official Music Video`,
        `${artist} ${title} official video`,
        `"${artist}" "${title}"`,
    ];
    const byId = new Map<string, YtCandidate>();
    for (const q of queries) {
        const url =
            "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=8" +
            `&q=${encodeURIComponent(q)}&key=${encodeURIComponent(apiKey)}`;
        const res = await fetch(url);
        if (!res.ok) continue;
        const data = await res.json();
        for (const it of data.items ?? []) {
            const id = it?.id?.videoId as string | undefined;
            if (!id || byId.has(id)) continue;
            byId.set(id, {
                id,
                title: it.snippet?.title ?? "",
                channel: it.snippet?.channelTitle ?? "",
                thumb:
                    it.snippet?.thumbnails?.medium?.url ??
                    it.snippet?.thumbnails?.default?.url,
            });
        }
        if (byId.size >= 12) break;
    }
    return pickMusicVideo([...byId.values()], artist, title, allowAnyChannel);
}
