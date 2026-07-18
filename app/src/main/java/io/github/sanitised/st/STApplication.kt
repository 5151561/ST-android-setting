package io.github.sanitised.st

import android.app.Application
import io.github.sanitised.st.data.AppContainer

class STApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }
}
