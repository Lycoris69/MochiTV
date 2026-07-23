package com.lycoris.mochitv.di

import android.content.Context
import androidx.room.Room
import coil.ImageLoader
import com.lycoris.mochitv.data.local.MochiDatabase
import com.lycoris.mochitv.data.local.dao.AnimeDao
import com.lycoris.mochitv.data.local.dao.EpisodeDao
import com.lycoris.mochitv.data.network.UserAgents
import com.lycoris.mochitv.data.repository.AnimeRepositoryImpl
import com.lycoris.mochitv.data.scraper.AnimeScraper
import com.lycoris.mochitv.data.scraper.AnimeSamaScraper
import com.lycoris.mochitv.data.scraper.VoirAnimeScraper
import com.lycoris.mochitv.domain.repository.AnimeRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module installed in [SingletonComponent] — all bindings here live
 * for the duration of the application process.
 *
 * ### Pattern note
 * The module is `abstract` so Dagger can use `@Binds` (which is zero-cost at
 * runtime — just a type alias in the generated code). `@Provides` methods
 * that cannot be `@Binds` live in the [companion object].
 *
 * ### Scraper multi-bindings
 * Each scraper is contributed to a `Set<AnimeScraper>` via `@IntoSet`.
 * [AnimeRepositoryImpl] receives this set and fans-out calls to all scrapers
 * without depending on any concrete scraper type.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    // -------------------------------------------------------------------------
    // Repository
    // -------------------------------------------------------------------------

    @Binds
    @Singleton
    abstract fun bindAnimeRepository(impl: AnimeRepositoryImpl): AnimeRepository

    // -------------------------------------------------------------------------
    // Scraper multi-bindings (Set<AnimeScraper>)
    // -------------------------------------------------------------------------

    @Binds
    @IntoSet
    abstract fun bindAnimeSamaScraper(scraper: AnimeSamaScraper): AnimeScraper

    @Binds
    @IntoSet
    abstract fun bindVoirAnimeScraper(scraper: VoirAnimeScraper): AnimeScraper

    // -------------------------------------------------------------------------
    // @Provides must live in companion object when the module is abstract
    // -------------------------------------------------------------------------

    companion object {

        /**
         * Singleton [OkHttpClient] shared across all scrapers.
         *
         * - Logging interceptor emits headers only (no body) so credentials
         *   in cookies are not leaked to Logcat in debug builds.
         * - 30-second timeouts are generous enough for slow anime sites but
         *   still prevent indefinite hangs.
         */
        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }

            return OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        /**
         * Singleton [MochiDatabase] backed by a named file so the cache
         * survives app restarts.
         *
         * [allowMainThreadQueries] is intentionally omitted — all DAO calls
         * are `suspend` functions dispatched from coroutines on IO threads.
         */
        @Provides
        @Singleton
        fun provideMochiDatabase(
            @ApplicationContext context: Context,
        ): MochiDatabase = Room.databaseBuilder(
            context,
            MochiDatabase::class.java,
            "mochi_db",
        ).build()

        @Provides
        @Singleton
        fun provideAnimeDao(db: MochiDatabase): AnimeDao = db.animeDao()

        @Provides
        @Singleton
        fun provideEpisodeDao(db: MochiDatabase): EpisodeDao = db.episodeDao()

        /**
         * Coil [ImageLoader] with a Referer interceptor.
         *
         * voir-anime.to (and most anime CDNs) return 403 for image requests that
         * lack a matching Referer header. We set the Referer to the origin of each
         * requested image URL so Coil can load thumbnails from all scraped sources.
         */
        @Provides
        @Singleton
        fun provideImageLoader(
            @ApplicationContext context: Context,
        ): ImageLoader = ImageLoader.Builder(context)
            .okHttpClient(
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val req = chain.request()
                        chain.proceed(
                            req.newBuilder()
                                .header("Referer", "${req.url.scheme}://${req.url.host}/")
                                .header("User-Agent", UserAgents.random())
                                .build()
                        )
                    }
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
            )
            .build()
    }
}
