# MochiTV

An Android app for watching anime, built with Kotlin and Jetpack Compose. Content is scraped from two French-language sources and unified into a single browsable catalogue.

**Sources**
- [anime-sama.to](https://anime-sama.to/)
- [voir-anime.to](https://voir-anime.to/)

> **Personal use only.** This app is a private project built for personal viewing. It does not host, distribute, or store any copyrighted content — all media is streamed directly from the third-party source sites. Do not redistribute the APK or use this project for commercial purposes.

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

| Scraper | Search | Episodes | Stream |
|---|---|---|---|
| `AnimeSamaScraper` | POST `/template-php/defaut/fetch.php` | `episodes.js` per season (JS array of embed URLs) | Embed page → extractor chain |
| `VoirAnimeScraper` | `?s=` WordPress search | Madara theme `li.wp-manga-chapter` | iframe → embed page → extractor chain |

Stream extraction tries three extractors in order: `VidmolyExtractor` → `StreamtapeExtractor` → `GenericExtractor`.

IDs are source-prefixed (e.g. `animesama_naruto`, `voir_naruto`) so results from different scrapers coexist without collision.

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

## Changelog

### v1.1.6
- Download button now reflects the real download state: spinner while resolving or downloading, green checkmark when complete, red error icon on failure (tap to retry)
- Download states persist across screen navigation — already-downloaded episodes show the checkmark when you reopen the detail screen

### v1.1.5
- Downloads now work for HLS streams (Vidmoly, voir-anime, etc.) — switched from the system DownloadManager to Media3's `DownloadService` which reassembles all HLS segments automatically
- Downloaded episodes are cached in the app's external files directory and play offline via ExoPlayer's `CacheDataSource`

### v1.1.4
- Fixed crash when tapping the download button (`Result.map` was not catching exceptions thrown by the system DownloadManager — changed to `mapCatching` so errors surface as a snackbar instead of killing the app)

### v1.1.3
- Download episodes to `Downloads/MochiTV/<anime>/<episode>.mp4` via the system DownloadManager
- Download button appears on every episode row; spins while the stream URL is being resolved
- Progress and completion are shown in the system notification tray
- HLS streams surface a clear "cannot download" message instead of silently failing

### v1.1.2
- Anime-sama episodes now use **Sibnet** (player 3) by default — reliable playback with no bot protection issues
- All player arrays (eps1–eps5) are parsed; best source is chosen automatically (sibnet > vidmoly > streamtape > others > embed4me)
- Sibnet stream URL is resolved server-side (follows the CDN redirect with the correct Referer), so ExoPlayer receives a direct signed MP4 URL

### v1.1.1
- Fixed playback for anime-sama episodes hosted on embed4me.com (player uses a headless WebView to bypass Cloudflare bot protection and intercept the HLS stream URL)

### v1.0.10
- Search history: the last 10 searches are saved locally
- Tap the search field while empty to see recent searches
- Tap an entry to re-run it, × to remove one, or "Clear all" to wipe the list

### v1.0.9
- New **Library** tab: bookmark any series and find it later
- Bookmark toggle button (filled / outlined) in the detail screen top bar
- Bottom navigation bar (Home / Library) on all root screens

### v1.0.8
- Replaced animesamatv.su (tiny catalogue) with **anime-sama.to** — large French catalogue with Naruto, One Piece, and thousands more titles

### v1.0.7
- New app icon: MochiTV mascot replaces the placeholder

### v1.0.6
- README updates (personal-use disclaimer, changelog)

### v1.0.5
- Thumbnails now load at full resolution (460×630 px) by parsing `srcset` instead of the small default `src` (110×150 px)

### v1.0.4
- Fixed synopsis not appearing on the detail screen
- Fixed images not loading (added Referer header to Coil image requests)

### v1.0.3 / v1.0.2
- Infinite scroll for latest releases on the home screen
- Fixed empty video player — stream URLs now extracted via two-level iframe resolution

### v1.0.1
- Latest releases section on the home screen (voir-anime.to)
- Anime thumbnails in search results and detail screen

### v1.0.0
- Initial release: search, anime detail, episode list, video playback

---

## Roadmap

- [x] Search history
- [x] Download support
