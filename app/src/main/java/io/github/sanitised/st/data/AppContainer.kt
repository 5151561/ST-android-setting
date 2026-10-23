package io.github.sanitised.st.data

import android.app.Application
import io.github.sanitised.st.AppPaths
import io.github.sanitised.st.DEFAULT_PORT
import io.github.sanitised.st.SillyTavernUrl
import io.github.sanitised.st.api.TauriTavernJniClient
import io.github.sanitised.st.api.TavernCoreApi
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
 * 应用级手动依赖容器。所有数据层入口通过同一个 provider 获取 [TavernCoreApi]，
 * 默认由基于 JNI 的 [TauriTavernJniClient] 提供，不再依赖本地 HTTP 端口。
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
    private val clientFactory: (String) -> TavernCoreApi = { TauriTavernJniClient.instance },
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
 * 数据层统一 API 入口工厂，直接返回基于 JNI 的 [TauriTavernJniClient]，避免 UI 屏幕直接依赖 HTTP 客户端。
 */
fun tavernApi(baseUrl: String = ""): TavernCoreApi = TauriTavernJniClient.instance
fun tavernApiForPort(port: Int = DEFAULT_PORT): TavernCoreApi = TauriTavernJniClient.instance
