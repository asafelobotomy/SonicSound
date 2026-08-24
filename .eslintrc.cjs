{
  "root": true,
  "env": {
    "browser": true,
    "es2020": true
  },
  "extends": ["eslint:recommended", "plugin:react/recommended", "prettier"],
  "parserOptions": {
    "ecmaVersion": "latest",
    "sourceType": "module",
    "ecmaFeatures": { "jsx": true }
  },
  "settings": {
    "react": { "version": "detect" }
  },
  "rules": {
    "no-console": "warn",
    "react/react-in-jsx-scope": "off",
    "max-lines": [
      "error",
      { "max": 400, "skipBlankLines": true, "skipComments": true }
    ]
  },
  "ignorePatterns": ["build/", "node_modules/", "android/", "src-tauri/"]
}
