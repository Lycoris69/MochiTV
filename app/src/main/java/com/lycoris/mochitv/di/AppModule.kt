package com.lycoris.mochitv.di

import com.lycoris.mochitv.data.repository.AnimeRepositoryImpl
import com.lycoris.mochitv.data.scraper.AnimeScraper
import com.lycoris.mochitv.data.scraper.FlixnetScraper
import com.lycoris.mochitv.data.scraper.VoirAnimeScraper
import com.lycoris.mochitv.domain.repository.AnimeRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
    abstract fun bindFlixnetScraper(scraper: FlixnetScraper): AnimeScraper

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
    }
}
