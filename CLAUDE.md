# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MochiTV is a Kotlin Android application for watching anime. It scrapes content from:
- `https://anime-sama.to/` — large French-language catalogue (VOSTFR/VF)
- `https://voir-anime.to/` — French Madara WordPress theme site

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.example.mochitv.YourTestClass"

# Run instrumented (on-device) tests
./gradlew connectedAndroidTest

# Lint
./gradlew lint

# Clean build
./gradlew clean
```

## Release Process

```bash
cp app/build/outputs/apk/release/app-release.apk mochitv-X.Y.Z.apk
gh release create vX.Y.Z mochitv-X.Y.Z.apk --title "MochiTV vX.Y.Z" --notes "..."
```

**Never** use `gh release create file#name` — the `#name` syntax is unreliable. Always `cp` first.
**Never** add `Co-Authored-By: Claude` to commits.

## Architecture

This project follows **MVVM + Clean Architecture**:

- `data/` — Scrapers, network clients (OkHttp), Room cache, repository implementations
- `domain/` — Use cases and repository interfaces (pure Kotlin, no Android deps)
- `presentation/` — ViewModels, Fragments/Activities, and UI state classes
- `di/` — Dependency injection modules (Hilt)

### Scraping Layer

Each source site has its own scraper class implementing `AnimeScraper`. Scrapers use **Jsoup** for HTML parsing. Network calls use **OkHttp** dispatched off the main thread via coroutines.

| Scraper | File | Search endpoint | Episode source | Stream extraction |
|---|---|---|---|---|
| `AnimeSamaScraper` | `data/scraper/AnimeSamaScraper.kt` | POST `/template-php/defaut/fetch.php` | `episodes.js` per season (JS `var eps1 = [...]`) | Embed URL directly from eps1 |
| `VoirAnimeScraper` | `data/scraper/VoirAnimeScraper.kt` | `?s=` WordPress search | Madara theme `li.wp-manga-chapter` | iframe → embed page |

Stream extraction uses a three-extractor chain: `VidmolyExtractor` → `StreamtapeExtractor` → `GenericExtractor`.

Scrapers are bound as `Set<AnimeScraper>` via Hilt `@Binds @IntoSet` multi-binding. `AnimeRepositoryImpl` fans-out all calls concurrently with `coroutineScope + async`.

IDs are source-prefixed (`animesama_naruto`, `voir_naruto`) so results coexist without collision.

### Playback

Video playback uses **ExoPlayer** (Media3). Stream URLs are extracted by the scraper/extractor chain and passed to `PlayerViewModel`.

### Bookmarks / Library

`BookmarkEntity` stores `animeId` + timestamp in Room. `BookmarkRepositoryImpl` joins with `AnimeDao` to return full `Anime` objects. The `LibraryScreen` shows bookmarked series in a 3-column grid. The bookmark toggle lives in the `DetailScreen` TopAppBar.

### Key Libraries

| Purpose | Library | Version |
|---|---|---|
| HTTP client | OkHttp | 4.12.0 |
| HTML parsing | Jsoup | 1.18.3 |
| DI | Hilt | 2.53.1 |
| Image loading | Coil | 2.7.0 |
| Video playback | AndroidX Media3 / ExoPlayer | 1.4.1 |
| Async | Kotlin Coroutines + Flow | — |
| Navigation | Jetpack Navigation Compose | 2.8.5 |
| UI | Jetpack Compose + Material 3 | BOM 2024.12.01 |
| Local cache | Room | 2.6.1 |
| JSON (TypeConverters) | Gson | — |

Min SDK: 26 · Target SDK: 35 · AGP: 8.7.3 · Kotlin: 2.1.0

### Room Database

- `MochiDatabase` version **2** (version 1 → 2 added `bookmarks` table; `fallbackToDestructiveMigration()` is set)
- Entities: `AnimeEntity`, `EpisodeEntity`, `BookmarkEntity`
- DAOs: `AnimeDao`, `EpisodeDao`, `BookmarkDao`

## Scraping Notes

- **anime-sama.to**: search is a POST (not GET); episodes come from a per-season `episodes.js` file at `{catalogueUrl}/{season}/{lang}/episodes.js`. `srcset` parsing not needed (images are already full-res).
- **voir-anime.to**: CDN returns 403 without a `Referer` header — Coil's `ImageLoader` sets it automatically via an OkHttp interceptor in `AppModule`. Parse `srcset` to get high-res thumbnails (460×630) instead of the tiny `src` default (110×150).
- Rotate User-Agent headers using `UserAgents.random()` from `data/network/UserAgents.kt`.

## Navigation

Bottom nav bar (Home / Library) is rendered by `AppNavGraph` only when the current destination is a root tab (`home` or `library`). It hides automatically on Detail and Player screens.
