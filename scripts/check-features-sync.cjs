#!/usr/bin/env node
/**
 * Fails if src/features.ts youtubeMusicVideos disagrees with Features.kt YOUTUBE_MUSIC_VIDEOS.
 */
const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const tsPath = path.join(root, "src/features.ts");
const ktPath = path.join(root, "android/app/src/main/kotlin/app/sonicsound/Features.kt");

function readBool(file, patterns, label) {
  const text = fs.readFileSync(file, "utf8");
  for (const re of patterns) {
    const m = text.match(re);
    if (m) {
      const raw = m[1].toLowerCase();
      if (raw === "true") return true;
      if (raw === "false") return false;
    }
  }
  console.error(`Could not parse ${label} in ${path.relative(root, file)}`);
  process.exit(1);
}

const tsVal = readBool(
  tsPath,
  [/youtubeMusicVideos\s*:\s*(true|false)/],
  "youtubeMusicVideos"
);
const ktVal = readBool(
  ktPath,
  [/YOUTUBE_MUSIC_VIDEOS\s*=\s*(true|false)/i],
  "YOUTUBE_MUSIC_VIDEOS"
);

if (tsVal !== ktVal) {
  console.error(
    `Feature flag mismatch: src/features.ts youtubeMusicVideos=${tsVal} ` +
      `vs Features.kt YOUTUBE_MUSIC_VIDEOS=${ktVal}`
  );
  process.exit(1);
}

console.log(`OK: youtubeMusicVideos / YOUTUBE_MUSIC_VIDEOS both ${tsVal}`);
