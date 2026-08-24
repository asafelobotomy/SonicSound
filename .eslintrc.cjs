module.exports = {
  root: true,
  env: {
    browser: true,
    es2020: true,
  },
  parser: "@typescript-eslint/parser",
  parserOptions: {
    ecmaVersion: "latest",
    sourceType: "module",
    ecmaFeatures: { jsx: true },
  },
  plugins: ["@typescript-eslint", "react"],
  extends: ["prettier"],
  settings: {
    react: { version: "detect" },
  },
  rules: {
    // Gate for the 400 LOC hard rule (Kotlin/Java covered by lint:loc).
    "max-lines": [
      "error",
      { max: 400, skipBlankLines: true, skipComments: true },
    ],
    "no-debugger": "error",
  },
  ignorePatterns: [
    "build/",
    "dist/",
    "node_modules/",
    "android/",
    "src-tauri/",
    "*.d.ts",
  ],
};
