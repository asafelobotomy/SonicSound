import { Toast } from "@capacitor/toast";
import { faUser } from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { useFocusable } from "@noriginmedia/norigin-spatial-navigation";
import classNames from "classnames";
import { useCallback, useContext } from "react";
import { AppContext } from "../AppContext";
import { IAccount } from "../Models/AppContext";
import VLC from "../Plugins/VLC";

export default function Account() {
    const { context, setContext } = useContext(AppContext);
    const logout = useCallback(async () => {
        const cleared: IAccount = {
            username: null,
            url: "",
            password: "",
            type: "",
            usePlaintext: false,
        };
        try {
            const ret = await VLC.logout();
            if (ret.status === "ok") {
                setContext(ret.value ?? cleared);
            } else {
                setContext(cleared);
                Toast.show({ text: ret.error || "Logout failed" });
            }
        } catch {
            setContext(cleared);
            Toast.show({ text: "Logout failed" });
        }
    }, [setContext]);
    const { focused, ref } = useFocusable({ onEnterPress: logout });

    return (
        <div className="d-flex flex-column align-items-center justify-content-start overflow-scroll scrollable">
            <div className="text-white account-icon-container">
                <FontAwesomeIcon icon={faUser} size="5x" />
            </div>
            <div className="text-header text-white">{context.username}</div>
            <div className="text-white">on {context.url}</div>
            <div className="text-white">running {context.type}</div>
            {context.usePlaintext && (
                <div className="text-danger">using plaintext password</div>
            )}
            <div className="logout-button-container">
                <button
                    ref={ref}
                    className={classNames(
                        "btn",
                        "mt-10",
                        focused ? "btn-selected" : "btn-primary"
                    )}
                    onClick={logout}
                >
                    Logout
                </button>
            </div>
        </div>
    );
}
