import { Toast } from "@capacitor/toast";
import { useCallback, useContext } from "react";
import { MenuContext } from "../AppContext";
import { IPlaylist } from "../Models/API/Responses/IPlaylistsResponse";
import VLC from "../Plugins/VLC";

export function useAddToPlaylist() {
    const { setMenuContext } = useContext(MenuContext);

    return useCallback(
        async (songId: string) => {
            const playlists = await VLC.getPlaylists();
            const add = async (playlist: IPlaylist | null) => {
                const ret = await VLC.addToPlaylist({
                    id: playlist?.id ?? null,
                    songId,
                });
                if (ret.status === "ok") {
                    Toast.show({ text: "Song added to playlist" });
                } else {
                    Toast.show({ text: ret.error });
                }
            };
            if (playlists.status !== "ok") {
                Toast.show({ text: playlists.error });
                return;
            }
            setMenuContext({
                x: "10vw",
                y: "10vh",
                show: true,
                body: (
                    <div
                        className="d-flex flex-column px-2"
                        style={{ width: "80vw", height: "80vh" }}
                    >
                        <div className="text-white w-100 section-header mb-3">
                            Add to playlist
                        </div>
                        <div className="d-flex flex-column w-100 h-100 scrollable overflow-scroll">
                            <button
                                className="btn btn-primary mb-2"
                                onClick={() => add(null)}
                            >
                                Create playlist and add
                            </button>
                            {playlists.value?.map((playlist) => (
                                <button
                                    key={playlist.id}
                                    className="btn btn-primary mb-1"
                                    onClick={() => add(playlist)}
                                >
                                    {playlist.name}
                                </button>
                            ))}
                        </div>
                    </div>
                ),
            });
        },
        [setMenuContext]
    );
}
