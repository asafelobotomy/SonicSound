import { faMagnifyingGlass } from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import classNames from "classnames";
import { useCallback, useContext, useEffect, useRef, useState } from "react";
import { FixedSizeGrid as Grid, GridChildComponentProps } from "react-window";
import { StateContext } from "../AppContext";
import { IAlbumArtistResponse } from "../Models/API/Responses/IArtistResponse";
import AlbumCard from "./AlbumCard";
import "./Artists.scss";
import Loading from "./Loading";
import useAutoFill from "../Hooks/useAutoFill";
import VLC from "../Plugins/VLC";
import {
    AlbumSort,
    albumSortOptions,
    sortAlbums,
} from "../library/sort";

export default function Albums() {
    const [albums, setAlbums] = useState<IAlbumArtistResponse[]>([]);
    const [filteredAlbums, setFilteredAlbums] = useState<
        IAlbumArtistResponse[]
    >([]);
    const [sort, setSort] = useState<AlbumSort>("name-asc");
    const [fetched, setFetched] = useState<boolean>(false);
    const [canSearch, setCanSearch] = useState<boolean>(false);
    const searchRef = useRef<HTMLInputElement>(null);
    const { stateContext } = useContext(StateContext);

    const gridRef = (ref: Grid) => {
        if (
            ref &&
            (stateContext.selectedAlbum[0] !== 0 ||
                stateContext.selectedAlbum[1] !== 0)
        ) {
            ref.scrollToItem({
                columnIndex: stateContext.selectedAlbum[1],
                rowIndex: stateContext.selectedAlbum[0],
                align: "center",
            });
        }
    };

    useEffect(() => {
        const fetch = async () => {
            const al = await VLC.getAlbums();
            if (al.status === "ok") {
                setAlbums(al.value!);
                setFilteredAlbums(sortAlbums(al.value!, "name-asc"));
            }
            setFetched(true);
        };
        if (!fetched) {
            fetch();
        }
    }, [fetched]);

    const applyFilter = useCallback(
        (source: IAlbumArtistResponse[], query: string, nextSort: AlbumSort) => {
            const q = query.trim().toUpperCase();
            const filtered = q
                ? source.filter((s) => s.name.toUpperCase().includes(q))
                : source;
            setFilteredAlbums(sortAlbums(filtered, nextSort));
        },
        []
    );

    const search = (val: any) => {
        applyFilter(albums, val.target.value, sort);
    };

    useEffect(() => {
        if (canSearch) {
            searchRef.current!.focus();
        }
    }, [canSearch]);

    const { gridProps, autoFillRef, columnCount } = useAutoFill(filteredAlbums);

    const AlbumCardWrapper = useCallback(
        ({
            data,
            style,
            columnIndex,
            rowIndex,
        }: GridChildComponentProps<IAlbumArtistResponse[]>) => {
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
                    <AlbumCard
                        item={data[index]}
                        forceWidth={false}
                        columnIndex={columnIndex}
                        rowIndex={rowIndex}
                    />
                </div>
            );
        },
        [columnCount]
    );

    if (albums.length === 0) {
        return (
            <div className="row" style={{ height: "100%" }}>
                <div
                    className="col-12 d-flex align-items-center justify-content-center"
                    style={{ height: "100%" }}
                >
                    <Loading />
                </div>
            </div>
        );
    }

    return (
        <>
            <div className="artist-container d-flex flex-column">
                <div className="d-flex flex-row align-items-center justify-content-between w-100">
                    <div className="section-header text-white">Albums</div>
                    <div className="d-flex flex-row align-items-center gap-2">
                        <select
                            className="form-select form-select-sm"
                            style={{ width: "auto" }}
                            value={sort}
                            onChange={(e) => {
                                const next = e.target.value as AlbumSort;
                                setSort(next);
                                applyFilter(
                                    albums,
                                    searchRef.current?.value ?? "",
                                    next
                                );
                            }}
                        >
                            {albumSortOptions.map((o) => (
                                <option key={o.value} value={o.value}>
                                    {o.label}
                                </option>
                            ))}
                        </select>
                        <button
                            type="button"
                            className="btn btn-link text-white"
                            onClick={() => setCanSearch(!canSearch)}
                        >
                            <FontAwesomeIcon icon={faMagnifyingGlass} />
                        </button>
                    </div>
                </div>
                <input
                    ref={searchRef}
                    className={classNames(
                        "form-control",
                        "mb-2",
                        canSearch ? "" : "d-none"
                    )}
                    placeholder="Search..."
                    onKeyUp={search}
                />
                <div
                    ref={autoFillRef}
                    style={{ height: "100%", width: "100%" }}
                >
                    <Grid
                        ref={gridRef}
                        {...gridProps}
                        useIsScrolling={true}
                        style={{ overflowY: "auto", overflowX: "hidden" }}
                        itemData={filteredAlbums}
                    >
                        {AlbumCardWrapper}
                    </Grid>
                </div>
            </div>
        </>
    );
}
