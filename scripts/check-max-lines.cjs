#!/usr/bin/env node
/**
 * Fails if any .ts/.tsx/.kt/.java under src/ or android/app/src exceeds max LOC.
 */
const fs = require("fs");
const path = require("path");

const MAX = 400;
const ROOTS = ["src", "android/app/src"];
const EXT = new Set([".ts", ".tsx", ".kt", ".java"]);

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    if (ent.name === "node_modules" || ent.name === "build") continue;
    const p = path.join(dir, ent.name);
    if (ent.isDirectory()) walk(p, out);
    else if (EXT.has(path.extname(ent.name))) out.push(p);
  }
  return out;
}

const offenders = [];
for (const root of ROOTS) {
  for (const file of walk(root)) {
    const lines = fs.readFileSync(file, "utf8").split(/\r?\n/).length;
    if (lines > MAX) offenders.push({ file, lines });
  }
}

if (offenders.length) {
  console.error(`Files exceeding ${MAX} LOC:`);
  for (const o of offenders.sort((a, b) => b.lines - a.lines)) {
    console.error(`  ${o.lines}\t${o.file}`);
  }
  process.exit(1);
}
console.log(`OK: no source file exceeds ${MAX} LOC`);
