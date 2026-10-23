package io.github.sanitised.st

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import io.github.sanitised.st.data.AppContainer
import io.github.sanitised.st.ui.LocalTavernImageInterceptor
import javax.inject.Inject

@HiltAndroidApp
class STApplication : Application(), SingletonImageLoader.Factory {
    @Inject
    internal lateinit var injectedContainer: AppContainer

    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (::injectedContainer.isInitialized) {
            injectedContainer
        } else {
            AppContainer(this)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(LocalTavernImageInterceptor(this@STApplication))
            }
            .build()
    }
}
