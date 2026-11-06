import { seal } from "./qcCrypto";
import { runQuickConnect } from "./qcClient";

/** Browser sign-in page served by the TV at http://<tv-ip>:30003/#code=NNNNNN. */
const $ = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;

const form = $<HTMLFormElement>("qc-form");
const status = $<HTMLParagraphElement>("qc-status");
const submit = $<HTMLButtonElement>("qc-submit");
const codeInput = $<HTMLInputElement>("qc-code");

const fromHash = new URLSearchParams(location.hash.slice(1)).get("code");
if (fromHash && /^\d{6}$/.test(fromHash)) codeInput.value = fromHash;

function show(text: string, kind: "info" | "ok" | "error") {
    status.textContent = text;
    status.className = `status ${kind}`;
}

form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const code = codeInput.value.replace(/\D/g, "");
    if (code.length !== 6) {
        show("Enter the 6-digit code shown on the TV.", "error");
        return;
    }
    const account = {
        username: $<HTMLInputElement>("qc-user").value.trim(),
        password: $<HTMLInputElement>("qc-pass").value,
        url: $<HTMLInputElement>("qc-url").value.trim().replace(/\/+$/, ""),
        type: "",
        usePlaintext: $<HTMLInputElement>("qc-plain").checked,
    };
    submit.disabled = true;
    show("Connecting to the TV…", "info");
    const result = await runQuickConnect({
        baseUrl: location.origin,
        code,
        account,
        seal,
        onProgress: (text) => show(text, "info"),
    });
    show(result.message, result.ok ? "ok" : "error");
    if (result.ok) form.querySelectorAll("input").forEach((i) => (i.disabled = true));
    else submit.disabled = false;
});
