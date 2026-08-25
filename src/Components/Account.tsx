import { useFocusable } from "@noriginmedia/norigin-spatial-navigation";
import { faUser } from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import classNames from "classnames";
import { useCallback, useContext } from "react";
import { useNavigate } from "react-router-dom";
import { AppContext } from "../AppContext";
import { IAccount } from "../Models/AppContext";

export default function Account() {
    const { context, setContext } = useContext(AppContext);
    const navigate = useNavigate();
    const logout = useCallback(() => {
        const newContext: IAccount = {
            username: null,
            url: "",
            password: "",
            type: "",
            usePlaintext: false,
        };
        setContext(newContext);
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
            <button
                type="button"
                className="btn btn-outline-light mt-3"
                onClick={() => navigate("/settings")}
            >
                Open settings
            </button>
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
