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

**Immersive mode:** `PlayerScreen` uses `WindowInsetsControllerCompat` to hide/show the status and navigation bars in sync with `PlayerView`'s controller visibility. `WindowCompat.setDecorFitsSystemWindows(false)` is set on entry; bars are restored on dispose.

**Next episode:** `PlayerViewModel` keeps a mutable `currentEpisodeId` (starts from the nav-arg but advances on `goToNextEpisode()`). After loading the episode list it sets `_nextEpisode` to the following entry.

**Player top bar:** When controls are visible, `PlayerScreen` overlays a custom top bar (gradient fade from black, `statusBarsPadding()`) containing three icon buttons: Back (ArrowBack → `onBack`), SkipNext (only when `nextEpisode != null` → `goToNextEpisode()`), and Home (navigates to the home root via `onHome`). The SkipNext button cycles the native `PlayerView.player` reference to force `PlayerControlView.updateNavigation()` when next-episode availability changes mid-playback. The native previous-track button icon is replaced with a circular replay arrow (`ic_replay` drawable) via `post { findViewById<ImageButton>(exo_prev)?.setImageResource(...) }`.

**Watch progress:** `PlayerScreen` polls `exoPlayer.currentPosition` every 10 s inside a `LaunchedEffect`. Once position ≥ 20 min or ≥ 4/5 of duration, it calls `viewModel.recordWatchProgress()` once, which invokes `RecordWatchProgressUseCase` to upsert a row into the `watch_progress` Room table.

### Bookmarks / Library

`BookmarkEntity` stores `animeId` + optional `folderId` + timestamp in Room. `BookmarkRepositoryImpl` joins with `AnimeDao` to return full `Anime` objects. The `LibraryScreen` shows bookmarked series in a 3-column grid. The bookmark toggle lives in the `DetailScreen` TopAppBar.

**Library Folders:** `LibraryFolderEntity` (`tableName = "library_folders"`) stores `id` (UUID) and `name`. `LibraryFolderDao` exposes `getAllWithCount(): Flow<List<FolderWithCount>>` (each row includes `seriesCount`) and `getName(id): String?`. At the Library root, a horizontal `LazyRow` of folder chips appears above the grid; tapping a chip navigates into that folder (filters the grid). Long-press enters selection mode; the folder icon in the top bar opens `MoveFolderDialog`. `LibraryViewModel.currentFolderId: StateFlow<String?>` drives navigation state — `null` = root, non-null = inside a folder.

**Offline note:** To reach a detail page offline you must have bookmarked it (Library) or have it in the Room anime cache from a prior visit. The episode list falls back to `EpisodeDao.getAllByAnimeId()` via `recoverCatching` in `AnimeRepositoryImpl.getEpisodes()` when the network is unavailable.

### Key Libraries

| Purpose | Library | Version |
|---|---|---|
| HTTP client | OkHttp | 4.12.0 |
| HTML parsing | Jsoup | 1.18.3 |
| DI | Hilt | 2.53.1 |
| Image loading | Coil | 2.7.0 |
| Video playback | AndroidX Media3 / ExoPlayer | 1.4.1 |
| Offline downloads | AndroidX Media3 (media3-exoplayer-hls, media3-database) | 1.4.1 |
| Async | Kotlin Coroutines + Flow | — |
| Navigation | Jetpack Navigation Compose | 2.8.5 |
| UI | Jetpack Compose + Material 3 | BOM 2024.12.01 |
| Local cache | Room | 2.6.1 |
| JSON (TypeConverters) | Gson | — |

Min SDK: 26 · Target SDK: 35 · AGP: 8.7.3 · Kotlin: 2.1.0

### Theme / Typography

`presentation/theme/Typography.kt` defines `NunitoFamily` — a public `FontFamily` backed by `res/font/nunito.ttf` (a variable font; all four `Font()` entries for Normal/Medium/SemiBold/Bold point to the same file and the wght axis handles them at render time).

`NunitoFamily` is applied via `fontFamily = com.lycoris.mochitv.presentation.theme.NunitoFamily` on the `Text` inside each screen's `TopAppBar` title block (Home, Library, Downloads, Detail). It is **not** wired into `MaterialTheme.typography` — only top bar title text uses it.

All `TopAppBar` composables use `expandedHeight = 30.dp` to produce a compact 30dp bar.

### Downloads

Offline episode downloads are handled by **Media3 `DownloadService`** (supports both MP4 and HLS/m3u8).

| File | Role |
|---|---|
| `data/download/MochiDownloadService.kt` | Extends Media3 `DownloadService`; shows a foreground notification during downloads |
| `data/download/DownloadManagerHolder.kt` | Process-scoped singleton (`object`) providing `DownloadManager` + `SimpleCache`; double-checked locking |
| `data/download/EpisodeDownloader.kt` | `@Singleton` injected into ViewModels; exposes `enqueue()`, `observeStates(): Flow<Map<String,Int>>`, `getDownloadedUri(id): String?`, and `deleteDownload(id)` (calls `DownloadService.sendRemoveDownload`) |
| `domain/usecase/DownloadEpisodeUseCase.kt` | Resolves stream URL then calls `EpisodeDownloader.enqueue()`; uses `mapCatching` (not `map`) |
| `presentation/detail/EpisodeDownloadState.kt` | Sealed interface: `None / Resolving / Queued / Downloading / Completed / Failed` |

**Cancelling downloads:** `EpisodeDownloader.cancelDownload(id)` calls `DownloadService.sendRemoveDownload` (same as `deleteDownload` — Media3 removes regardless of state). `DetailViewModel.cancelDownload(id)` clears `_resolvingEpisodes` first (handles the pre-Media3 resolving phase) then delegates. In `DetailScreen`, the `Resolving/Queued/Downloading` branch renders an `IconButton` containing a stacked `CircularProgressIndicator` + small `Close` icon — tap to cancel immediately.

Downloaded content lives in `<externalFilesDir>/media3_downloads/`. `PlayerScreen` builds ExoPlayer with a `CacheDataSource.Factory` backed by `DownloadManagerHolder.getCache(context)` so downloaded episodes play fully offline. `PlayerViewModel` checks `getDownloadedUri()` first — if the episode is cached, it skips all network stream resolution.

`DetailViewModel` combines `_resolvingEpisodes: MutableStateFlow<Set<String>>` with `episodeDownloader.observeStates()` via `combine()` to produce `episodeDownloadStates: StateFlow<Map<String, EpisodeDownloadState>>`.

**Deleting downloads (Downloads tab):** `DownloadsViewModel` exposes a `deleteDialogState: StateFlow<DeleteDialogState?>`. Tapping the trash icon calls `openDeleteDialog()`, which queries `EpisodeDao.getAllByAnimeId()` for each selected series and filters to episodes where `getDownloadedUri() != null`. All episodes are checked by default (`DeleteDialogState.checkedIds`). `confirmDelete()` fires `EpisodeDownloader.deleteDownload()` for each checked id. `DownloadDeleteState.kt` holds the `EpisodeItem` and `DeleteDialogState` data classes.

Required manifest entries: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `MochiDownloadService` with `foregroundServiceType="dataSync"`.

### Playback Position / Resume & History

`PlaybackPositionEntity` (`tableName = "playback_positions"`) stores `episodeId` (PK), `animeId`, `positionMs`, `durationMs`, `savedAtMs`. Unlike `WatchProgressEntity` (a one-shot "seen" flag), this table is a live cursor — it is upserted continuously and deleted when the episode is finished (within 5 s of end).

| File | Role |
|---|---|
| `data/local/entity/PlaybackPositionEntity.kt` | Room entity; `toDomain()` maps to `PlaybackPosition` |
| `data/local/dao/PlaybackPositionDao.kt` | `@Upsert save()`, `get(episodeId)`, `observeAll()` (newest-first), `observeByAnimeId()`, `delete(episodeId)` |
| `domain/model/PlaybackPosition.kt` | Domain model; computed `progress: Float` (positionMs / durationMs) |
| `domain/usecase/SavePlaybackPositionUseCase.kt` | Upserts position; auto-deletes when within last 5 s of duration (episode finished) |
| `domain/usecase/GetPlaybackPositionUseCase.kt` | Suspend lookup by episodeId → `PlaybackPosition?` |
| `domain/usecase/GetPlaybackPositionsForAnimeUseCase.kt` | `Flow<Map<String, PlaybackPosition>>` for the detail screen progress bars |
| `domain/usecase/GetWatchHistoryUseCase.kt` | Joins position rows with `AnimeEntity` + `EpisodeEntity`; emits `List<WatchHistoryItem>` (max 10, newest-first) |
| `presentation/home/WatchHistoryItem.kt` | UI model: `PlaybackPosition` + `Anime` + `Episode` |

`PlayerScreen` saves position in `DisposableEffect.onDispose` (covers back, home, next episode) and on `ON_PAUSE` lifecycle events (screen lock, app switch). On open it seeks to `savedPositionMs` from `PlayerUiState.Ready`. `DetailScreen` shows a `LinearProgressIndicator` under each episode row that has a saved incomplete position. `LibraryScreen` shows a horizontal "Continue Watching" card row (with progress bars) at the root level above the folder chips.

### Watch Progress / Seen Indicator

`WatchProgressEntity` (`tableName = "watch_progress"`) has `episodeId: String` (PK) and `watchedAtMs: Long`. The table is upserted — replaying an episode just updates the timestamp.

| File | Role |
|---|---|
| `data/local/entity/WatchProgressEntity.kt` | Room entity |
| `data/local/dao/WatchProgressDao.kt` | `@Upsert markWatched()` + `observeWatchedIds(): Flow<List<String>>` |
| `domain/usecase/RecordWatchProgressUseCase.kt` | Called from `PlayerViewModel.recordWatchProgress()` |
| `domain/usecase/GetWatchedEpisodeIdsUseCase.kt` | Maps DAO Flow to `Flow<Set<String>>`; consumed by `DetailViewModel` |

`DetailViewModel` exposes `watchedEpisodeIds: StateFlow<Set<String>>`. `DetailScreen` passes `isWatched = episode.id in watchedEpisodeIds` to each `EpisodeRow`, which renders a 12dp `CheckCircle` icon + "Seen" label when true.

### Room Database

- `MochiDatabase` version **7** (`fallbackToDestructiveMigration()` is set — no migration scripts)
  - v1 → v2: added `bookmarks` table
  - v2 → v3: added `search_history` table
  - v3 → v4: added `lastReleaseDate` to `anime`, `releaseDate` to `episode`
  - v4 → v5: added `watch_progress` table
  - v5 → v6: added `library_folders` table; added `folderId` column to `bookmarks`
  - v6 → v7: added `playback_positions` table
- Entities: `AnimeEntity`, `EpisodeEntity`, `BookmarkEntity`, `SearchHistoryEntity`, `WatchProgressEntity`, `LibraryFolderEntity`, `PlaybackPositionEntity`
- DAOs: `AnimeDao`, `EpisodeDao`, `BookmarkDao`, `SearchHistoryDao`, `WatchProgressDao`, `LibraryFolderDao`, `PlaybackPositionDao`

## Scraping Notes

- **anime-sama.to**: search is a POST (not GET); episodes come from a per-season `episodes.js` file at `{catalogueUrl}/{season}/{lang}/episodes.js`. `srcset` parsing not needed (images are already full-res). No date metadata available.
- **voir-anime.to**: CDN returns 403 without a `Referer` header — Coil's `ImageLoader` sets it automatically via an OkHttp interceptor in `AppModule`. Parse `srcset` to get high-res thumbnails (460×630) instead of the tiny `src` default (110×150).
  - Latest release date: scraped from `div.chapter-item span.post-date` (or `span.post-date` fallback) in `getLatestReleases` → `Anime.lastReleaseDate`
  - Episode release date: scraped from `.chapter-release-date i, .chapter-date i` in `getEpisodes` → `Episode.releaseDate`
  - Both are best-effort and nullable; no UI breakage if selectors miss.
- Rotate User-Agent headers using `UserAgents.random()` from `data/network/UserAgents.kt`.

## Navigation

Bottom nav bar (Home / Library / Downloads) is rendered by `AppNavGraph` only when the current destination is a root tab (`home`, `library`, or `downloads`). It hides automatically on Detail and Player screens.
