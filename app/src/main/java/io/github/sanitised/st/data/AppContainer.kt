package io.github.sanitised.st.data

import android.app.Application
import io.github.sanitised.st.AppPaths
import io.github.sanitised.st.DEFAULT_PORT
import io.github.sanitised.st.SillyTavernUrl
import io.github.sanitised.st.api.TavernCoreApi
import io.github.sanitised.st.api.TavernCoreClient
import io.github.sanitised.st.data.preferences.UserPreferencesRepository
import io.github.sanitised.st.data.repository.ConnectionRepository
import io.github.sanitised.st.data.repository.DefaultConnectionRepository
import io.github.sanitised.st.data.repository.DefaultGroupChatRepository
import io.github.sanitised.st.data.repository.DefaultPersonaPresetRepository
import io.github.sanitised.st.data.repository.DefaultWorldInfoRepository
import io.github.sanitised.st.data.repository.GroupChatRepository
import io.github.sanitised.st.data.repository.PersonaPresetRepository
import io.github.sanitised.st.data.repository.WorldInfoRepository

/**
 * 应用级手动依赖容器。动态端口属于连接配置，而不是界面状态；所有数据层入口
 * 通过同一个 provider 读取它，避免每个屏幕各自拼接地址和创建客户端。
 */
class AppContainer(application: Application) {
    private val localLibraryReader = LocalTavernLibraryReader(AppPaths(application).dataDir)

    val tavernClientProvider = LocalTavernClientProvider()
    val userPreferencesRepository = UserPreferencesRepository(application)
    val characterRepository: CharacterRepository = DefaultCharacterRepository(
        clientProvider = tavernClientProvider::get,
        localReader = localLibraryReader,
    )
    val worldInfoRepository: WorldInfoRepository = DefaultWorldInfoRepository(
        clientProvider = tavernClientProvider::get,
    )
    val connectionRepository: ConnectionRepository = DefaultConnectionRepository(
        clientProvider = tavernClientProvider::get,
    )
    val personaPresetRepository: PersonaPresetRepository = DefaultPersonaPresetRepository(
        clientProvider = tavernClientProvider::get,
    )
    val groupChatRepository: GroupChatRepository = DefaultGroupChatRepository(
        clientProvider = tavernClientProvider::get,
        localReader = localLibraryReader,
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

/**
 * 数据层统一 API 入口工厂，避免 UI 屏幕直接依赖和实例化 [TavernCoreClient] 实现类。
 */
fun tavernApi(baseUrl: String): TavernCoreApi = TavernCoreClient(baseUrl = baseUrl)
fun tavernApiForPort(port: Int): TavernCoreApi = TavernCoreClient(baseUrl = SillyTavernUrl.localWebUrl(port))

