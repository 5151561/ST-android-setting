package io.github.sanitised.st

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.sanitised.st.data.AppContainer
import javax.inject.Inject

@HiltAndroidApp
class STApplication : Application() {
    @Inject
    internal lateinit var injectedContainer: AppContainer

    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (::injectedContainer.isInitialized) {
            injectedContainer
        } else {
            AppContainer(this)
        }
    }
}
