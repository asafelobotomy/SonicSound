export type JukeboxTab =
    | "random"
    | "genre"
    | "artist"
    | "decade"
    | "similar"
    | "starred"
    | "server";

export type ICollectionPayload =
    | { type: "random" }
    | { type: "genre"; genre: string }
    | { type: "artist"; artistId: string; artistName: string }
    | { type: "decade"; fromYear: number; toYear: number }
    | { type: "similar"; seedSongId: string; seedTitle: string }
    | { type: "starred" }
    | { type: "server"; playlistId: string; playlistName: string };

export function buildCollectionJson(collection: ICollectionPayload): string {
    switch (collection.type) {
        case "random":
            return JSON.stringify({ type: "random" });
        case "genre":
            return JSON.stringify({ type: "genre", genre: collection.genre });
        case "artist":
            return JSON.stringify({
                type: "artist",
                artistId: collection.artistId,
                artistName: collection.artistName,
            });
        case "decade":
            return JSON.stringify({
                type: "decade",
                fromYear: collection.fromYear,
                toYear: collection.toYear,
            });
        case "similar":
            return JSON.stringify({
                type: "similar",
                seedSongId: collection.seedSongId,
                seedTitle: collection.seedTitle,
                currentSeedId: collection.seedSongId,
            });
        case "starred":
            return JSON.stringify({ type: "starred" });
        case "server":
            return JSON.stringify({
                type: "server",
                playlistId: collection.playlistId,
                playlistName: collection.playlistName,
            });
        default: {
            const _exhaustive: never = collection;
            return JSON.stringify(_exhaustive);
        }
    }
}
