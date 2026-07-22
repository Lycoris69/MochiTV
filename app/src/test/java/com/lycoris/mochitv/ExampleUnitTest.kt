package com.lycoris.mochitv

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Placeholder unit test.
 *
 * Replace with real ViewModel / use-case tests using `kotlinx-coroutines-test`
 * and MockK:
 *
 * ```kotlin
 * @Test
 * fun `search emits Loading then Success`() = runTest {
 *     val useCase = mockk<SearchAnimeUseCase>()
 *     coEvery { useCase(any()) } returns Result.success(emptyList())
 *     val vm = HomeViewModel(useCase)
 *     vm.onSearchQueryChange("naruto")
 *     // advance time past the debounce
 *     advanceTimeBy(600)
 *     assertEquals(HomeUiState.Success(emptyList()), vm.uiState.value)
 * }
 * ```
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}
