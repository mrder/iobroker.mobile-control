package com.mobilecontrol.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Installs a [TestDispatcher] as `Dispatchers.Main` for the duration of a test.
 *
 * Every ViewModel under test here builds `viewModelScope` (or calls `.stateIn(viewModelScope, ...)`)
 * eagerly - often right in a property initializer - which resolves `Dispatchers.Main` immediately.
 * On the plain JVM unit test classpath this project runs on (no real Android Main looper, no
 * Robolectric shadow), that throws "Module with the Main dispatcher had failed to initialize"
 * unless a Main dispatcher has been installed first via `Dispatchers.setMain`.
 *
 * [UnconfinedTestDispatcher] is used by default so that `viewModelScope.launch { ... }` bodies -
 * which in these tests only await hand-written fake suspend functions that never actually suspend -
 * run eagerly to completion instead of requiring an explicit `advanceUntilIdle()` after every call.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
