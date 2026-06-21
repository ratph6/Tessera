#!/usr/bin/env bash
# Regenerate the Dokka API site and push it. Cloudflare Pages is connected to the GitHub repo, so the
# push to main is the deploy — CF rebuilds from docs/ (preset: none, build: none, output dir: docs).
#
# Run from anywhere:  bash scripts/deploy-docs.sh
# (Dokka must run locally because Minecraft 26.1.2 isn't on public maven, so CI can't build it.)
set -euo pipefail

# repo root, regardless of where this is invoked from
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> generating Dokka HTML"
./gradlew dokkaGenerate

echo "==> syncing docs/"
rm -rf docs
mkdir docs
cp -r build/dokka/html/. docs/
touch docs/.nojekyll          # harmless on CF; needed if also served via GitHub Pages

git add -A docs
if git diff --cached --quiet; then
  echo "==> docs unchanged — nothing to deploy"
  exit 0
fi

git commit -m "docs: deploy $(date -u +%Y-%m-%dT%H:%MZ)"
git push origin main
echo "==> pushed — Cloudflare Pages will build from docs/"
