package com.lycoris.mochitv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Placeholder instrumented test.
 *
 * Instrumented tests run on an Android device or emulator and have access
 * to the full Android framework. Use the Compose testing APIs for UI tests:
 *
 * ```kotlin
 * @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()
 *
 * @Test
 * fun homeScreen_showsSearchField() {
 *     composeTestRule.onNodeWithText("Search anime…").assertIsDisplayed()
 * }
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.lycoris.mochitv", appContext.packageName)
    }
}
