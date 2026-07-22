# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MochiTV is a Kotlin Android application for watching anime. It scrapes content from:
- `https://flixnet.site/animesama/`
- `https://voir-anime.to/`

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

## Architecture

This project follows **MVVM + Clean Architecture**:

- `data/` — Scrapers, network clients (OkHttp/Retrofit), and repository implementations
- `domain/` — Use cases and repository interfaces (pure Kotlin, no Android deps)
- `presentation/` — ViewModels, Fragments/Activities, and UI state classes
- `di/` — Dependency injection modules (Hilt)

### Scraping Layer

Each source site has its own scraper class implementing a shared `AnimeScraper` interface. Scrapers use **Jsoup** for HTML parsing. Network calls are made with **OkHttp** and executed off the main thread via coroutines.

Scraper classes live in `data/scraper/` and are named after their source (e.g., `FlixnetScraper`, `VoirAnimeScraper`).

### Playback

Video playback uses **ExoPlayer** (Media3). Stream URLs are extracted by scraper classes and passed to a player ViewModel.

### Key Libraries (planned)

| Purpose | Library |
|---|---|
| HTTP client | OkHttp |
| HTML parsing | Jsoup |
| DI | Hilt |
| Image loading | Coil |
| Video playback | AndroidX Media3 / ExoPlayer |
| Async | Kotlin Coroutines + Flow |
| Navigation | Jetpack Navigation Component |
| UI | Jetpack Compose |

## Scraping Notes

- Both source sites render HTML server-side, so Jsoup is sufficient — no headless browser needed.
- Episode stream links may be embedded in `<iframe>` tags or JavaScript variables; extractors for those should live alongside the scraper in `data/scraper/extractor/`.
- Rotate User-Agent headers to avoid simple bot detection. Keep a list of common UA strings in a constants file.
- Cache scraped catalogue data locally (Room) to reduce network requests and support offline browsing.
