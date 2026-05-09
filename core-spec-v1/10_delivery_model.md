# Delivery Model

## Overview

All scenes (games) are hosted on GitHub and served as static files via GitHub Pages.
The app fetches the catalog, the user adds a scene, all its assets are downloaded atomically and stored locally. After that the scene runs fully offline.

## Repository Structure

```
/catalog.json
/scenes/{sceneId}/scene.json
/scenes/{sceneId}/assets/sounds/*.ogg
/scenes/{sceneId}/assets/images/*.png
/scenes/{sceneId}/assets/animations/*.json
```

## Catalog

`catalog.json` is the index of all available scenes:

```json
{
  "version": 1,
  "scenes": [
    {
      "id": "sort_fruits_01",
      "title": "Sort the Fruits",
      "description": "Drag fruits into matching baskets",
      "thumbnail": "scenes/sort_fruits_01/assets/images/thumbnail.png",
      "ageRange": [2, 5],
      "tags": ["sorting", "colors"],
      "assetManifest": "scenes/sort_fruits_01/manifest.json",
      "version": "1.0.0"
    }
  ]
}
```

## Asset Manifest

Each scene has a `manifest.json` listing all downloadable files with their sizes and checksums:

```json
{
  "sceneId": "sort_fruits_01",
  "version": "1.0.0",
  "totalSizeBytes": 4200000,
  "files": [
    { "path": "scene.json",                       "sha256": "abc123", "sizeBytes": 4200 },
    { "path": "assets/sounds/success.ogg",        "sha256": "def456", "sizeBytes": 38000 },
    { "path": "assets/images/apple.png",          "sha256": "ghi789", "sizeBytes": 120000 },
    { "path": "assets/animations/sparkle.json",   "sha256": "jkl012", "sizeBytes": 8000 }
  ]
}
```

## Asset References in scene.json

Assets are referenced by relative paths from the scene root:

```json
{
  "type": "playSoundOnEvent",
  "config": {
    "sound": "assets/sounds/success.ogg"
  }
}
```

The engine resolves these paths against the scene's local storage directory at runtime. No absolute URLs in scene.json.

## Download Flow

```
User taps "Add scene"
       ↓
Fetch manifest.json
       ↓
Calculate total size → show to user
       ↓
Download all files in manifest
       ↓
Verify sha256 for each file
       ↓
If all verified → commit to local storage → scene available
If any fail     → delete all downloaded files → show error
```

Rules:
- Download is **atomic**: all files or none
- Checksum verification is mandatory — corrupted files are rejected
- Partial downloads are not saved
- Re-download replaces the entire scene (no partial updates in v1)

## Local Storage Layout

```
local/{sceneId}/scene.json
local/{sceneId}/assets/sounds/*.ogg
local/{sceneId}/assets/images/*.png
local/{sceneId}/assets/animations/*.json
local/{sceneId}/.meta.json       ← download timestamp, version, checksums
```

## Catalog Refresh

- App fetches `catalog.json` on launch (if online)
- If offline, shows locally cached catalog
- Version field on each scene entry indicates if a re-download is available
- No automatic updates — user initiates re-download explicitly

## Invariants

1. A scene is either fully available locally or not available at all.
2. scene.json always uses relative asset paths — never absolute URLs.
3. The engine never fetches assets at runtime — all assets must be local.
4. sha256 verification happens before any file is committed to local storage.
5. Deleting a scene removes its entire local directory.
