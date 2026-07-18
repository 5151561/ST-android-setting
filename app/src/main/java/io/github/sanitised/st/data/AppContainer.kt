package io.github.sanitised.st.data

import android.app.Application
import io.github.sanitised.st.DEFAULT_PORT
import io.github.sanitised.st.AppPaths
import io.github.sanitised.st.SillyTavernUrl
import io.github.sanitised.st.api.TavernCoreApi
import io.github.sanitised.st.api.TavernCoreClient

/**
 * 应用级手动依赖容器。动态端口属于连接配置，而不是界面状态；所有数据层入口
 * 通过同一个 provider 读取它，避免每个屏幕各自拼接地址和创建客户端。
 */
class AppContainer(application: Application) {
    val tavernClientProvider = LocalTavernClientProvider()
    val characterRepository: CharacterRepository = DefaultCharacterRepository(
        clientProvider = tavernClientProvider::get,
        localReader = LocalTavernLibraryReader(AppPaths(application).dataDir),
    )

    fun updateLocalPort(port: Int) {
        tavernClientProvider.updatePort(port)
    }
}

class LocalTavernClientProvider(
    initialPort: Int = DEFAULT_PORT,
    private val clientFactory: (String) -> TavernCoreApi = ::TavernCoreClient,
) {
    @Volatile
    private var localPort: Int = initialPort.validPort()

    fun updatePort(port: Int) {
        localPort = port.validPort()
    }

    fun get(): TavernCoreApi = clientFactory(SillyTavernUrl.localWebUrl(localPort))

    internal fun currentPort(): Int = localPort

    private fun Int.validPort(): Int = takeIf { it in 1..65535 } ?: DEFAULT_PORT
}
