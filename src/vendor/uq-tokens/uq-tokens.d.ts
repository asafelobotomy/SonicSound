// Types for the vendored uq-tokens.js (SonicSound's addition; the .js is copied unchanged from
// uQ's web/uq-tokens). Keep in step with the version in its header.
export const VERSION: string;
export const VARIANT_ATTRIBUTE: string;
export const PROPERTIES: readonly string[];

export type TokensRoot = {
  style: {
    setProperty(name: string, value: string): void;
    removeProperty(name: string): unknown;
  };
  setAttribute(name: string, value: string): void;
  removeAttribute(name: string): void;
};

export function parseTokens(text: unknown): Record<string, unknown> | null;
export function mapTokens(tokens: unknown): {
  props: Record<string, string>;
  variant: "dark" | "light" | null;
};
export function applyTokens(tokens: unknown, root?: TokensRoot): number;
export function clearTokens(root?: TokensRoot): void;
export function validColor(v: unknown): string | null;
export function validLength(v: unknown): string | null;
export function validFont(v: unknown): string | null;

type Unsubscribe = () => void;

export function watchTokens(options: {
  read: () => string | null | undefined | Promise<string | null | undefined>;
  subscribe?: (
    callback: () => void,
  ) => Unsubscribe | void | Promise<Unsubscribe | void>;
  root?: TokensRoot;
}): { ready: Promise<void>; refresh: () => Promise<void>; stop: () => void };
