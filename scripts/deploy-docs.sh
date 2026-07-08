#!/usr/bin/env bash
# Regenerate the Dokka API site and push it. Cloudflare Pages is connected to the GitHub repo, so the
# push to main is the deploy — CF rebuilds from docs/ (preset: none, build: none, output dir: docs).
#
# Run from anywhere:  bash scripts/deploy-docs.sh
# (Dokka must run locally because Minecraft 26.2 isn't on public maven, so CI can't build it.)
set -euo pipefail

# repo root, regardless of where this is invoked from
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# the push below deploys main — running from another branch would push a stale local main
branch="$(git branch --show-current)"
if [ "$branch" != "main" ]; then
  echo "==> refusing to deploy from branch '$branch' (checkout main first)" >&2
  exit 1
fi

echo "==> generating Dokka HTML"
./gradlew dokkaGenerate

echo "==> syncing docs/"
rm -rf docs
mkdir docs
cp -r build/dokka/html/. docs/
touch docs/.nojekyll          # harmless on CF; needed if also served via GitHub Pages

git add -A docs
if git diff --cached --quiet -- docs; then
  echo "==> docs unchanged — nothing to deploy"
  exit 0
fi

# pathspec commit: whatever else the user had staged must not ride along
git commit -m "docs: deploy $(date -u +%Y-%m-%dT%H:%MZ)" -- docs
git push origin main
echo "==> pushed — Cloudflare Pages will build from docs/"
