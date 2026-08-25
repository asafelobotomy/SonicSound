export type AlbumSort =
    | "name-asc"
    | "name-desc"
    | "year-asc"
    | "year-desc"
    | "artist-asc";

export type ArtistSort =
    | "name-asc"
    | "name-desc"
    | "albums-asc"
    | "albums-desc";

export const albumSortOptions: { value: AlbumSort; label: string }[] = [
    { value: "name-asc", label: "Name A–Z" },
    { value: "name-desc", label: "Name Z–A" },
    { value: "year-asc", label: "Year ↑" },
    { value: "year-desc", label: "Year ↓" },
    { value: "artist-asc", label: "Artist A–Z" },
];

export const artistSortOptions: { value: ArtistSort; label: string }[] = [
    { value: "name-asc", label: "Name A–Z" },
    { value: "name-desc", label: "Name Z–A" },
    { value: "albums-asc", label: "Fewer albums" },
    { value: "albums-desc", label: "More albums" },
];

export function sortAlbums<T extends { name: string; year?: number; artist?: string }>(
    items: T[],
    sort: AlbumSort
): T[] {
    const copy = [...items];
    switch (sort) {
        case "name-asc":
            return copy.sort((a, b) => a.name.localeCompare(b.name));
        case "name-desc":
            return copy.sort((a, b) => b.name.localeCompare(a.name));
        case "year-asc":
            return copy.sort((a, b) => (a.year ?? 0) - (b.year ?? 0));
        case "year-desc":
            return copy.sort((a, b) => (b.year ?? 0) - (a.year ?? 0));
        case "artist-asc":
            return copy.sort((a, b) =>
                (a.artist ?? "").localeCompare(b.artist ?? "")
            );
        default: {
            const _exhaustive: never = sort;
            return _exhaustive;
        }
    }
}

export function sortArtists<T extends { name: string; albumCount?: number }>(
    items: T[],
    sort: ArtistSort
): T[] {
    const copy = [...items];
    switch (sort) {
        case "name-asc":
            return copy.sort((a, b) => a.name.localeCompare(b.name));
        case "name-desc":
            return copy.sort((a, b) => b.name.localeCompare(a.name));
        case "albums-asc":
            return copy.sort(
                (a, b) => (a.albumCount ?? 0) - (b.albumCount ?? 0)
            );
        case "albums-desc":
            return copy.sort(
                (a, b) => (b.albumCount ?? 0) - (a.albumCount ?? 0)
            );
        default: {
            const _exhaustive: never = sort;
            return _exhaustive;
        }
    }
}
