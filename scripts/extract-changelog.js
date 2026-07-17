#!/usr/bin/env node
/* Prints the CHANGELOG.md section for one version, e.g. `node scripts/extract-changelog.js 0.1.0`.
 * Used by the release workflow to fill in the GitHub Release body. */
'use strict';

const fs = require('fs');
const path = require('path');

const version = process.argv[2];
if (!version) {
    console.error('Usage: extract-changelog.js <version>');
    process.exit(1);
}

const changelog = fs.readFileSync(path.join(__dirname, '..', 'CHANGELOG.md'), 'utf8');
const lines = changelog.split('\n');

const startIndex = lines.findIndex((line) => line.startsWith('## ') && line.includes(`[${version}]`));
if (startIndex === -1) {
    console.error(`No CHANGELOG.md section found for version ${version}`);
    process.exit(1);
}

let endIndex = lines.findIndex((line, i) => i > startIndex && line.startsWith('## '));
if (endIndex === -1) {
    endIndex = lines.length;
}

console.log(lines.slice(startIndex + 1, endIndex).join('\n').trim());
