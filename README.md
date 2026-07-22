# MochiTV

An Android app for watching anime, built with Kotlin and Jetpack Compose. Content is scraped from two French-language sources and unified into a single browsable catalogue.

**Sources**
- [flixnet.site/animesama](https://flixnet.site/animesama/)
- [voir-anime.to](https://voir-anime.to/)

---

## Tech stack

| Layer | Library | Version |
|---|---|---|
| UI | Jetpack Compose + Material 3 | BOM 2024.12.01 |
| Navigation | Navigation Compose | 2.8.5 |
| DI | Hilt | 2.53.1 |
| Video playback | AndroidX Media3 / ExoPlayer | 1.4.1 |
| Image loading | Coil | 2.7.0 |
| HTTP client | OkHttp | 4.12.0 |
| HTML parsing | Jsoup | 1.18.3 |
| Local cache | Room | 2.6.1 |
| Async | Kotlin Coroutines + Flow | — |
| Build | AGP 8.7.3 · Kotlin 2.1.0 · KSP 2.1.0 | — |

Min SDK: 26 · Target SDK: 35

---

## Architecture

MVVM + Clean Architecture with three layers:

```
presentation/   ← Compose screens, ViewModels, UI state
domain/         ← Use cases, repository interfaces, models (pure Kotlin)
data/           ← Scrapers, OkHttp, Room, repository implementations
di/             ← Hilt modules
```

### Scraping

Each source has its own `AnimeScraper` implementation. Both are bound into a `Set<AnimeScraper>` via Hilt multi-binding, so `AnimeRepositoryImpl` fans searches out to all sources concurrently using `coroutineScope + async` — adding a third source requires only a new scraper class and one `@Binds @IntoSet` line.

Stream URL extraction tries three strategies in order: `<iframe src>` → JS `var videoUrl` → JWPlayer `file:` key.

IDs are source-prefixed (e.g. `flixnet_naruto`, `voir_naruto`) so results from different scrapers coexist without collision.

---

## Building

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Unit tests
./gradlew test

# Single test class
./gradlew test --tests "com.lycoris.mochitv.SomeTest"

# On-device tests
./gradlew connectedAndroidTest

# Lint
./gradlew lint
```

---

## Roadmap

- [ ] Room integration for offline catalogue cache
- [ ] Anime detail screen
- [ ] Episode list screen
- [ ] Media3 player screen
- [ ] Search history
- [ ] Download support
