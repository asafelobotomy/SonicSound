import { useForm } from "react-hook-form";
import { AppContext } from "../AppContext";
import { useCallback, useContext, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import logo from "../assets/logo.png";
import { motion, useAnimation } from "framer-motion";
import { Toast } from "@capacitor/toast";
import AccountItem from "./AccountItem";
import VLC from "../Plugins/VLC";
import { IAccount } from "../Models/AppContext";
import AndroidTVPlugin from "../Plugins/AndroidTV";
import {
    FocusContext,
    useFocusable,
} from "@noriginmedia/norigin-spatial-navigation";
import classNames from "classnames";

interface FormData {
    username: string;
    password: string;
    url: string;
    usePlaintext: boolean;
}

export default function PlayTest() {
    const { context, setContext } = useContext(AppContext);
    const navigate = useNavigate();
    const [accounts, setAccounts] = useState<IAccount[]>([]);
    const [showPassword, setShowPassword] = useState<boolean>(false);
    const controls = useAnimation();

    const hash = useCallback(
        async (data: FormData) => {
            const ret = await VLC.login(data);
            if (ret.status === "ok") {
                setContext(ret.value!);
                navigate("/home", { replace: true, state: [] });
            } else {
                await Toast.show({
                    text: ret.error,
                });
            }
        },
        [navigate, setContext]
    );

    useEffect(() => {
        AndroidTVPlugin.addListener("login", (info: any) => {
            hash(info);
        });
    }, [hash]);
    const {
        register,
        handleSubmit,
        formState: { errors },
        setFocus,
    } = useForm<FormData>();
    const onSubmit = handleSubmit(hash);

    const del = (url: string) => {
        setAccounts(accounts.filter((s) => s.url !== url));
    };

    const {
        ref: usernameRef,
        focused: usernameFocused,
    } = useFocusable({
        onEnterPress: () => {
            setFocus("username");
        },
    });
    const { ref: passwordRef, focused: passwordFocused } = useFocusable({
        onEnterPress: () => {
            setFocus("password");
        },
    });
    const { ref: urlRef, focused: urlFocused } = useFocusable({
        onEnterPress: () => {
            setFocus("url");
        },
    });
    const { focusKey, ref: parentRef } = useFocusable();
    const { ref: buttonRef, focused: buttonFocused } = useFocusable({
        onEnterPress: () => {
            onSubmit();
        },
    });

    useEffect(() => {
        const run = async () => {
            if (context.username !== "" && context.username !== null) {
                navigate("/home", { replace: true });
            } else if (context.username === null) {
                controls.start({ rotate: 0, scale: 1 });
                const ret = await VLC.getAccounts();
                if (ret.status === "ok") {
                    setAccounts(ret.value!);
                } else {
                    Toast.show({ text: ret.error });
                }
            }
        };
        run();
    }, [context, controls, navigate]);

    useEffect(() => {
        if (buttonFocused) {
            buttonRef.current.focus();
        }
    }, [buttonFocused, buttonRef]);

    return (
        <FocusContext.Provider value={focusKey}>
            <div
                ref={parentRef}
                className={"row d-flex align-items-center"}
                style={{ height: "100vh" }}
            >
                <form onSubmit={onSubmit}>
                    <motion.div
                        className="container"
                        initial={{ scale: 0, y: 125 }}
                        animate={{ rotate: 0, scale: 1, y: 0 }}
                        transition={{
                            type: "spring",
                            stiffness: 150,
                            damping: 20,
                        }}
                    >
                        <div className="col-12 mb-3">
                            <img src={logo} className="App-logo" alt="logo" />
                            <p className="text-white logo-text">SonicSound</p>
                        </div>
                    </motion.div>
                    <motion.div
                        className="container"
                        initial={{ scale: 0 }}
                        animate={controls}
                        transition={{
                            type: "spring",
                            stiffness: 150,
                            damping: 20,
                        }}
                    >
                        <div ref={usernameRef} className={"col-12 mb-3"}>
                            <input
                                {...register("username", { required: true })}
                                className={classNames(
                                    "form-control",
                                    usernameFocused ? "form-focused" : ""
                                )}
                                placeholder={"Username"}
                            />
                        </div>
                        {errors && errors.username && (
                            <div className="col-12 text-danger">
                                {errors.username.message}
                            </div>
                        )}
                        <div ref={passwordRef} className={"col-12 mb-3"}>
                            <div className="input-group">
                                <input
                                    {...register("password", { required: true })}
                                    type={showPassword ? "text" : "password"}
                                    className={classNames(
                                        "form-control",
                                        passwordFocused ? "form-focused" : ""
                                    )}
                                    placeholder={"Password"}
                                />
                                <button
                                    type="button"
                                    className="btn btn-outline-light"
                                    onClick={() => setShowPassword(!showPassword)}
                                    tabIndex={-1}
                                >
                                    {showPassword ? "Hide" : "Show"}
                                </button>
                            </div>
                        </div>
                        {errors && errors.password && (
                            <div className="col-12 text-danger">
                                {errors.password.message}
                            </div>
                        )}
                        <div ref={urlRef} className={"col-12 mb-3"}>
                            <input
                                {...register("url", { required: true })}
                                className={classNames(
                                    "form-control",
                                    urlFocused ? "form-focused" : ""
                                )}
                                placeholder={"Server URL"}
                            />
                        </div>
                        {errors && errors.url && (
                            <div className="col-12 text-danger">
                                {errors.url.message}
                            </div>
                        )}

                        <div className="form-check form-switch">
                            <input
                                className="form-check-input"
                                type="checkbox"
                                id="flexSwitchCheckDefault"
                                {...register("usePlaintext")}
                            />
                            <label className="w-100 text-start form-label text-white">
                                Use plaintext password (insecure on http
                                connections, needed for some servers)
                            </label>
                        </div>
                        {errors && errors.url && (
                            <div className="col-12 text-danger">
                                {errors.usePlaintext?.message}
                            </div>
                        )}

                        <button
                            ref={buttonRef}
                            type="submit"
                            className={classNames(
                                "btn",
                                buttonFocused ? "btn-selected" : "btn-primary",
                                "mb-3"
                            )}
                        >
                            Log In!
                        </button>

                        {accounts.length > 0 && (
                            <div className="d-flex flex-column align-items-center justify-content-center">
                                {accounts.map((s) => (
                                    <AccountItem account={s} del={del} />
                                ))}
                            </div>
                        )}
                    </motion.div>
                </form>
            </div>
        </FocusContext.Provider>
    );
}
