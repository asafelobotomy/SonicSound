import { useEffect } from "react";
import { useFocusable } from "@noriginmedia/norigin-spatial-navigation";
import classnames from "classnames";
import { ReactNode } from "react";

interface TVActionButtonProps {
    func: () => void;
    content: ReactNode;
    preferred?: boolean;
}

export default function TVActionButton({
    func,
    content,
    preferred,
}: TVActionButtonProps) {
    const { ref, focused, focusSelf } = useFocusable({ onEnterPress: func });
    useEffect(() => {
        if (preferred) {
            focusSelf();
        }
    }, [preferred, focusSelf]);
    return (
        <div
            ref={ref}
            className={classnames(
                "m-2",
                "p-2",
                "text-white",
                "tv-button",
                focused ? "btn-tv-selected" : ""
            )}
            onClick={func}
        >
            <div className="d-flex flex-column align-items-center">{content}</div>
        </div>
    );
}
