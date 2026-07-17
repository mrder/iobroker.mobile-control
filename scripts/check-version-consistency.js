#!/usr/bin/env node
/* Verifies that package.json, io-package.json and CHANGELOG.md agree on the current version.
 * If a GITHUB_REF_NAME / TAG env var is set (release workflow), it must match too. */
'use strict';

const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
const ioPkg = JSON.parse(fs.readFileSync(path.join(root, 'io-package.json'), 'utf8'));
const changelog = fs.readFileSync(path.join(root, 'CHANGELOG.md'), 'utf8');

const errors = [];

const pkgVersion = pkg.version;
const ioPkgVersion = ioPkg.common && ioPkg.common.version;

if (pkgVersion !== ioPkgVersion) {
    errors.push(`package.json version (${pkgVersion}) != io-package.json common.version (${ioPkgVersion})`);
}

if (!ioPkg.common.news || !ioPkg.common.news[pkgVersion]) {
    errors.push(`io-package.json common.news has no entry for version ${pkgVersion}`);
}

if (!changelog.includes(`[${pkgVersion}]`)) {
    errors.push(`CHANGELOG.md has no "[${pkgVersion}]" heading`);
}

const tag = process.env.RELEASE_TAG || process.env.GITHUB_REF_NAME;
if (tag) {
    const tagVersion = tag.replace(/^v/, '');
    if (tagVersion !== pkgVersion) {
        errors.push(`git tag "${tag}" (version ${tagVersion}) does not match package.json version (${pkgVersion})`);
    }
}

if (errors.length > 0) {
    console.error('Version consistency check failed:');
    for (const err of errors) {
        console.error(`  - ${err}`);
    }
    process.exit(1);
}

console.log(`Version consistency OK: ${pkgVersion}`);
