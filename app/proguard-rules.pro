# Add project-specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified in the
# Android SDK's default proguard-android-optimize.txt.
#
# For more details, see:
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Jsoup's parser intact when minifying
-keep class org.jsoup.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Hilt-generated components
-keep class dagger.hilt.** { *; }
-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel *;
}
