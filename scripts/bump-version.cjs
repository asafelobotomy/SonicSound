#!/usr/bin/env node
/**
 * Bump app version across package.json, lockfile, F-Droid metadata, and Tauri stubs.
 * Usage: node scripts/bump-version.cjs <x.y.z|major|minor|patch>
 */
const fs = require("fs");
const path = require("path");
const { execSync } = require("child_process");

const root = path.join(__dirname, "..");
const pkgPath = path.join(root, "package.json");
const metaPath = path.join(root, "metadata", "app.sonicsound.yml");
const tauriConf = path.join(root, "src-tauri", "tauri.conf.json");
const cargoToml = path.join(root, "src-tauri", "Cargo.toml");

function parseSemver(v) {
  const m = /^(\d+)\.(\d+)\.(\d+)$/.exec(v);
  if (!m) throw new Error(`Invalid semver: ${v}`);
  return { major: +m[1], minor: +m[2], patch: +m[3] };
}

function formatSemver({ major, minor, patch }) {
  return `${major}.${minor}.${patch}`;
}

function versionCode(v) {
  const { major, minor, patch } = parseSemver(v);
  return major * 10000 + minor * 100 + patch;
}

function nextVersion(current, bump) {
  const s = parseSemver(current);
  if (bump === "major") return formatSemver({ major: s.major + 1, minor: 0, patch: 0 });
  if (bump === "minor") return formatSemver({ major: s.major, minor: s.minor + 1, patch: 0 });
  if (bump === "patch") return formatSemver({ major: s.major, minor: s.minor, patch: s.patch + 1 });
  parseSemver(bump);
  return bump;
}

const arg = process.argv[2];
if (!arg) {
  console.error("Usage: node scripts/bump-version.cjs <x.y.z|major|minor|patch>");
  process.exit(1);
}

const pkg = JSON.parse(fs.readFileSync(pkgPath, "utf8"));
const version = nextVersion(pkg.version, arg);
const code = versionCode(version);

pkg.version = version;
fs.writeFileSync(pkgPath, `${JSON.stringify(pkg, null, 2)}\n`);

execSync("npm install --package-lock-only --ignore-scripts", {
  cwd: root,
  stdio: "inherit",
});

if (fs.existsSync(metaPath)) {
  let meta = fs.readFileSync(metaPath, "utf8");
  meta = meta.replace(
    /CurrentVersion: .*\nCurrentVersionCode: .*\n/,
    `CurrentVersion: ${version}\nCurrentVersionCode: ${code}\n`
  );
  if (!/^CurrentVersion:/m.test(meta)) {
    meta = `${meta.trimEnd()}\n\nCurrentVersion: ${version}\nCurrentVersionCode: ${code}\n`;
  }
  // Prepend a Builds entry if this version is not already listed
  if (!new RegExp(`versionName: ${version}\\b`).test(meta)) {
    const entry = `  - versionName: ${version}
    versionCode: ${code}
    commit: v${version}
    subdir: android/app
    sudo:
      - apt-get update || apt-get update
      - apt-get install -y openjdk-17-jdk-headless npm nodejs
    init: |
      cd ../..
      npm ci
      npm run build
      npx cap sync android
    gradle:
      - yes
    output: build/outputs/apk/release/app-release-unsigned.apk
    srclibs: []
    rm:
      - src-tauri
      - docs/screenshots
    prebuild:
      - sdkmanager "platforms;android-34" "build-tools;34.0.0" || true
    scanignore:
      - android/app/src/main/assets/public
`;
    meta = meta.replace(/^Builds:\n/m, `Builds:\n${entry}`);
  }
  fs.writeFileSync(metaPath, meta);
}

if (fs.existsSync(tauriConf)) {
  const conf = JSON.parse(fs.readFileSync(tauriConf, "utf8"));
  if (conf.package) conf.package.version = version;
  fs.writeFileSync(tauriConf, `${JSON.stringify(conf, null, 2)}\n`);
}

if (fs.existsSync(cargoToml)) {
  let cargo = fs.readFileSync(cargoToml, "utf8");
  cargo = cargo.replace(/^version = ".*"$/m, `version = "${version}"`);
  fs.writeFileSync(cargoToml, cargo);
}

console.log(`Bumped to ${version} (versionCode ${code})`);
console.log("Android versionName/versionCode are read from package.json at build time.");
