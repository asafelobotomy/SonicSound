import { Toast } from "@capacitor/toast";
import { useCallback, useEffect, useState } from "react";
import { FixedSizeGrid as Grid, GridChildComponentProps } from "react-window";
import { useNavigate } from "react-router-dom";
import useAutoFill from "../Hooks/useAutoFill";
import { IPlaylist } from "../Models/API/Responses/IPlaylistsResponse";
import VLC from "../Plugins/VLC";
import { PlaylistItem } from "./PlaylistItem";
import PlaylistItemCard from "./PlaylistItemCard";
import TVActionButton from "./TVActionButton";

export default function TVPlaylists() {
    const [playlists, setPlaylists] = useState<IPlaylist[]>([]);
    const [currentPlaylist, setCurrentPlaylist] = useState<IPlaylist>();
    const navigate = useNavigate();

    const fetch = useCallback(async () => {
        const ret = await VLC.getPlaylists();
        const current = await VLC.getCurrentPlaylist();
        if (current.status === "ok") {
            setCurrentPlaylist(current.value!!);
        }
        if (ret.status === "ok") {
            setPlaylists([...ret.value!!]);
        }
    }, []);

    useEffect(() => {
        fetch();
    }, [fetch]);

    const createPlaylist = useCallback(async () => {
        const ret = await VLC.createPlaylist({ songId: [], name: "New playlist" });
        if (ret.status === "ok" && ret.value) {
            Toast.show({ text: "Playlist created" });
            await fetch();
            navigate("/playlist", { state: { id: ret.value.id } });
        } else {
            Toast.show({ text: ret.error });
        }
    }, [fetch, navigate]);

    const deletePlaylist = useCallback(
        async (playlist: IPlaylist) => {
            const ret = await VLC.removePlaylist({ id: playlist.id });
            if (ret.status === "ok") {
                Toast.show({ text: "Playlist deleted" });
                fetch();
            } else {
                Toast.show({ text: ret.error });
            }
        },
        [fetch]
    );

    const { gridProps, autoFillRef, columnCount } = useAutoFill(playlists);

    const AlbumCardWrapper = useCallback(
        ({
            data,
            style,
            columnIndex,
            rowIndex,
        }: GridChildComponentProps<IPlaylist[]>) => {
            const index = rowIndex * columnCount + columnIndex;
            if (data[index] === undefined) {
                return <></>;
            }
            return (
                <div
                    style={{ ...style }}
                    key={`${rowIndex},${columnIndex}`}
                    id={`${rowIndex},${columnIndex}`}
                    className="d-flex flex-column align-items-center justify-content-center"
                >
                    <PlaylistItemCard item={data[index]} key={index} />
                    <TVActionButton
                        content="Delete"
                        func={() => deletePlaylist(data[index])}
                    />
                </div>
            );
        },
        [columnCount, deletePlaylist]
    );

    return (
        <div className="d-flex flex-column w-100 h-100 align-items-start playlist-container">
            <div className="d-flex flex-row align-items-center justify-content-between w-100 mb-2">
                <div className="section-header text-white mb-0">Playlists</div>
                <TVActionButton content="New playlist" func={createPlaylist} />
            </div>
            {currentPlaylist && (
                <div className="d-flex flex-row w-100">
                    <div className="list-group w-100">
                        <PlaylistItem item={currentPlaylist} playing={true} />
                    </div>
                </div>
            )}
            {playlists.length === 0 && (
                <div className="text-white-50 mt-2">
                    No playlists yet. Select New playlist to create one.
                </div>
            )}
            {playlists.length > 0 && (
                <>
                    <div className="section-header text-white mt-3">Playlists</div>
                    <div ref={autoFillRef} style={{ height: "100%", width: "100%" }}>
                        <Grid
                            {...gridProps}
                            useIsScrolling={true}
                            style={{ overflowY: "auto", overflowX: "hidden" }}
                            itemData={playlists}
                        >
                            {AlbumCardWrapper}
                        </Grid>
                    </div>
                </>
            )}
        </div>
    );
}
