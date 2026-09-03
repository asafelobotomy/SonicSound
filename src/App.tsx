import React, { useEffect, useState } from "react";
import "./App.scss";
import { Route, Routes, Navigate } from "react-router-dom";
import Home from "./Components/Home";
import PlayTest from "./Components/PlayTest";
import {
    AppContext,
    AppContextDefValue,
    MenuContextDefValue,
    IMenuContext,
    MenuContext,
    IStateContext,
    StateContextDefValue,
    StateContext,
} from "./AppContext";
import { IAccount } from "./Models/AppContext";
import Artists from "./Components/Artists";
import Artist from "./Components/Artist";
import Album from "./Components/Album";
import AudioControl from "./Components/AudioControl";
import { Helmet } from "react-helmet";
import { StatusBar } from "@capacitor/status-bar";
import Sidebar from "./Components/Sidebar";
import Navbar from "./Components/Navbar";
import Albums from "./Components/Albums";
import Loading from "./Components/Loading";
import CardContextMenu from "./Components/CardContextMenu";
import { Toast } from "@capacitor/toast";
import { Capacitor } from "@capacitor/core";
import Search from "./Components/Search";
import VLC from "./Plugins/VLC";
import Account from "./Components/Account";
import { init } from "@noriginmedia/norigin-spatial-navigation";
import Remote from "./Components/Remote";
import Jukebox from "./Components/Jukebox";
import { App as CapacitorApp } from "@capacitor/app";
import Playlists from "./Components/Playlists";
import Playlist from "./Components/Playlist";
import EditPlaylist from "./Components/EditPlaylist";
import Radio from "./Components/Radio";
import Settings from "./Components/Settings";
import Videos from "./Components/Videos";
import { Features } from "./features";

function App() {
    const [context, setContext] = useState<IAccount>(AppContextDefValue);

    const [menuContext, setMenuContext] =
        useState<IMenuContext>(MenuContextDefValue);
    const [stateContext, setStateContext] =
        useState<IStateContext>(StateContextDefValue);
    const [tried, setTried] = useState<boolean>(false);

    useEffect(() => {
        VLC.addListener("EX", (info) => {
            Toast.show({ text: info.error });
        });
    }, []);

    const contextValue = React.useMemo(
        () => ({
            context,
            setContext,
        }),
        [context]
    );

    const menuContextValue = React.useMemo(
        () => ({
            menuContext,
            setMenuContext,
        }),
        [menuContext]
    );

    const stateContextValue = React.useMemo(
        () => ({
            stateContext,
            setStateContext,
        }),
        [stateContext]
    );

    useEffect(() => {
        const fetch = async () => {
            if (Capacitor.getPlatform() === "android") {
                CapacitorApp.addListener("backButton", ({ canGoBack }) => {
                    if (canGoBack) {
                        window.history.back();
                    } else {
                        CapacitorApp.exitApp();
                    }
                });
            }
            init({
                // debug: true,
                // visualDebug: true
            });

            const c = await VLC.getActiveAccount();

            if (c.status === "ok") {
                setContext(c.value!);
            } else {
                setContext({ username: null, password: "", url: "", type: "", usePlaintext: false });
            }

            if (Capacitor.getPlatform() === "android") {
                StatusBar.setBackgroundColor({ color: "282c34" });
            }

            setTried(true);
            document.addEventListener("contextmenu", (event) => {
                event.preventDefault();
            });
            document.addEventListener("click", () => {
                setMenuContext({ body: "", show: false, x: 0, y: 0 });
            });
        };
        if (!tried) {
            fetch();
        }
    }, [tried]);
    const [navbarCollapsed, setNavbarCollapsed] = useState<boolean>(true);

    return (
        <>
            <StateContext.Provider value={stateContextValue}>
                <AppContext.Provider value={contextValue}>
                    <div className="App container-fluid d-flex flex-column justify-content-between main-content-pad">
                        <Helmet>
                            <title>SonicSound</title>
                        </Helmet>
                        <MenuContext.Provider value={menuContextValue}>
                            {context.username === "" && (
                                <div className="h-100 w-100 d-flex align-items-center justify-content-center">
                                    <Loading />
                                </div>
                            )}
                            {context.username === null && <PlayTest />}
                            {context.username !== "" &&
                                context.username !== null && (
                                    <>
                                        <Navbar
                                            navbarCollapsed={navbarCollapsed}
                                            setNavbarCollapsed={
                                                setNavbarCollapsed
                                            }
                                        />
                                        <Sidebar
                                            navbarCollapsed={navbarCollapsed}
                                            setNavbarCollapsed={
                                                setNavbarCollapsed
                                            }
                                        />
                                        <Routes>
                                            <Route
                                                path="/"
                                                element={
                                                    <Navigate
                                                        to="/home"
                                                        replace
                                                    />
                                                }
                                            />
                                            <Route
                                                path="/home"
                                                element={<Home />}
                                            />
                                            <Route
                                                path="/artists"
                                                element={<Artists />}
                                            />
                                            <Route
                                                path="/artist"
                                                element={<Artist />}
                                            />
                                            <Route
                                                path="/album"
                                                element={<Album />}
                                            />
                                            <Route
                                                path="/account"
                                                element={<Account />}
                                            />
                                            <Route
                                                path="/albums"
                                                element={<Albums />}
                                            />
                                            <Route
                                                path="/playlists"
                                                element={<Playlists />}
                                            />
                                            <Route
                                                path="/radio"
                                                element={<Radio />}
                                            />
                                            {Features.youtubeMusicVideos && (
                                                <Route
                                                    path="/videos"
                                                    element={<Videos />}
                                                />
                                            )}
                                            <Route
                                                path="/settings"
                                                element={<Settings />}
                                            />
                                            <Route
                                                path="/playlist"
                                                element={<Playlist />}
                                            />
                                            <Route
                                                path="/editPlaylist"
                                                element={<EditPlaylist />}
                                            />
                                            <Route
                                                path="/search"
                                                element={<Search />}
                                            />
                                            <Route
                                                path="/remote"
                                                element={<Remote />}
                                            />
                                            <Route
                                                path="/jukebox"
                                                element={<Jukebox />}
                                            />
                                            <Route
                                                path="/qr"
                                                element={
                                                    <Navigate
                                                        to="/remote"
                                                        replace
                                                    />
                                                }
                                            />
                                        </Routes>
                                        <AudioControl />
                                        <CardContextMenu {...menuContext} />
                                    </>
                                )}
                        </MenuContext.Provider>
                    </div>
                </AppContext.Provider>
            </StateContext.Provider>
        </>
    );
}

export default App;
