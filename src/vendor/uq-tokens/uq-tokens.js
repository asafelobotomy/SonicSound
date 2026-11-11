// uq-tokens 1.0.0
// Applies uQ's theme tokens (~/.local/state/uq/theme/tokens.json) to a web page as CSS custom
// properties. No dependencies, no file system access: the app passes the file text in.
// Every value is validated before it is set, so a broken or hostile file cannot inject CSS.
// This module never throws, never uses innerHTML or eval, and touches the root element only
// through style.setProperty/removeProperty and setAttribute/removeAttribute.

export const VERSION = "1.0.0";
export const VARIANT_ATTRIBUTE = "data-uq-variant";

// tokens.json `colors` keys. Each becomes --uq-<kebab-case key>; any other key is ignored.
const COLOR_KEYS = [
    "bg", "surface", "surface_alt", "overlay", "text", "text_muted",
    "accent", "accent_text", "border", "success", "warning", "error"
];

// [property, section, key]: lengths in px.
const LENGTHS = [
    ["--uq-radius", "ui", "radius"],
    ["--uq-gap", "ui", "gap_in"],
    ["--uq-gap-in", "ui", "gap_in"],
    ["--uq-gap-out", "ui", "gap_out"],
    ["--uq-border-width", "ui", "border_width"],
    ["--uq-font-size", "fonts", "size"]
];

// [property, key in `fonts`]: emitted as a quoted family name.
const FONTS = [
    ["--uq-font-ui", "ui"],
    ["--uq-font-mono", "mono"]
];

const kebab = (k) => "--uq-" + k.replace(/_/g, "-");

// Every custom property this module may set, in a stable order.
export const PROPERTIES = Object.freeze([
    ...COLOR_KEYS.map(kebab),
    ...LENGTHS.map((l) => l[0]),
    ...FONTS.map((f) => f[0])
]);

const COLOR_RE = /^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/;
const FONT_RE = /^[^;{}]{1,100}$/;
const MAX_LENGTH = 1000;

export function validColor(v) {
    return typeof v === "string" && COLOR_RE.test(v) ? v : null;
}

export function validLength(v) {
    return typeof v === "number" && Number.isFinite(v) && v >= 0 && v <= MAX_LENGTH ? v + "px" : null;
}

// A family name: no ; { } (spec), and also no quotes, backslashes, control characters
// (newlines included), url( or comment openers, since the name is wrapped in double quotes.
export function validFont(v) {
    if (typeof v !== "string" || !FONT_RE.test(v) || v.trim() === "") return null;
    if (/["'\\\u0000-\u001f\u007f]/.test(v) || /url\s*\(/i.test(v) || v.includes("/*")) return null;
    return '"' + v.trim() + '"';
}

function section(o, k) {
    const v = o[k];
    return v !== null && typeof v === "object" && !Array.isArray(v) ? v : {};
}

function isObject(o) {
    return o !== null && typeof o === "object" && !Array.isArray(o);
}

function defaultRoot() {
    return typeof document !== "undefined" && document ? document.documentElement : null;
}

// Returns the parsed object, or null for anything that is not a JSON object. Never throws.
export function parseTokens(text) {
    if (typeof text !== "string" || text.length === 0) return null;
    try {
        const o = JSON.parse(text);
        return isObject(o) ? o : null;
    } catch (e) {
        return null;
    }
}

// The property map tokens would produce (property -> CSS value), plus the variant or null.
export function mapTokens(tokens) {
    const props = {};
    let variant = null;
    if (!isObject(tokens)) return { props, variant };
    const colors = section(tokens, "colors"), fonts = section(tokens, "fonts");
    for (const k of COLOR_KEYS) {
        const v = validColor(colors[k]);
        if (v !== null) props[kebab(k)] = v;
    }
    for (const [prop, sec, k] of LENGTHS) {
        const v = validLength(section(tokens, sec)[k]);
        if (v !== null) props[prop] = v;
    }
    for (const [prop, k] of FONTS) {
        const v = validFont(fonts[k]);
        if (v !== null) props[prop] = v;
    }
    if (tokens.variant === "dark" || tokens.variant === "light") variant = tokens.variant;
    return { props, variant };
}

// Removes every property and the variant attribute this module sets.
export function clearTokens(root = defaultRoot()) {
    if (!root) return;
    try {
        for (const p of PROPERTIES) root.style.removeProperty(p);
        root.removeAttribute(VARIANT_ATTRIBUTE);
    } catch (e) { /* never throw */ }
}

// Sets the properties from a parsed tokens object; invalid or missing ones are removed, so
// nothing stale stays from an earlier call. Anything but an object clears. Returns the number
// of properties set.
export function applyTokens(tokens, root = defaultRoot()) {
    if (!root) return 0;
    if (!isObject(tokens)) { clearTokens(root); return 0; }
    const { props, variant } = mapTokens(tokens);
    let n = 0;
    try {
        for (const p of PROPERTIES) {
            if (p in props) { root.style.setProperty(p, props[p]); n++; }
            else root.style.removeProperty(p);
        }
        if (variant) root.setAttribute(VARIANT_ATTRIBUTE, variant);
        else root.removeAttribute(VARIANT_ATTRIBUTE);
    } catch (e) { /* never throw */ }
    return n;
}

const isThenable = (v) => v !== null && (typeof v === "object" || typeof v === "function") && typeof v.then === "function";

// Applies now and on every change. read() returns the file text or null (or a Promise of it);
// subscribe(cb) calls cb on change and may return an unsubscribe function (or a Promise of one,
// like Tauri's listen). Returns { refresh, stop }; refresh() resolves once applied.
export function watchTokens({ read, subscribe, root = defaultRoot() } = {}) {
    let seq = 0, stopped = false, unsub = null;
    const apply = (text) => { if (!stopped) applyTokens(parseTokens(text), root); };
    const refresh = () => {
        const mine = ++seq;
        let r;
        try { r = typeof read === "function" ? read() : null; } catch (e) { r = null; }
        if (!isThenable(r)) { apply(r); return Promise.resolve(); }
        return Promise.resolve(r).then((t) => t, () => null)
            .then((t) => { if (mine === seq) apply(t); });
    };
    const done = refresh();
    if (typeof subscribe === "function") {
        try {
            const u = subscribe(() => { refresh(); });
            if (isThenable(u)) Promise.resolve(u).then((f) => { if (stopped && typeof f === "function") f(); else unsub = f; }, () => {});
            else unsub = u;
        } catch (e) { /* no live updates */ }
    }
    return {
        ready: done,
        refresh,
        stop() {
            stopped = true;
            try { if (typeof unsub === "function") unsub(); } catch (e) { /* ignore */ }
        }
    };
}
