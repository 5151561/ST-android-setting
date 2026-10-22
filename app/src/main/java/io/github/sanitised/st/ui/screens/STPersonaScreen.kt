package io.github.sanitised.st.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.PersonaProfile
import io.github.sanitised.st.api.PersonaSaveRequest
import io.github.sanitised.st.data.repository.DefaultPersonaPresetRepository
import io.github.sanitised.st.data.repository.PersonaPresetRepository
import io.github.sanitised.st.data.tavernApi
import kotlinx.coroutines.launch

@Composable
fun STPersonaScreen(
    status: NodeStatus,
    baseUrl: String,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    personaPresetRepository: PersonaPresetRepository = remember(baseUrl) { DefaultPersonaPresetRepository { tavernApi(baseUrl) } }
) {
    val scope = rememberCoroutineScope()
    var personas by remember { mutableStateOf<List<PersonaProfile>>(emptyList()) }
    val running = status.state == NodeState.RUNNING

    fun loadPersonas() {
        if (running) {
            scope.launch {
                try {
                    val result = personaPresetRepository.listPersonas()
                    personas = result
                } catch (e: Exception) {
                    onShowMessage(e.message ?: "扮演者加载失败")
                }
            }
        }
    }

    LaunchedEffect(running, baseUrl, personaPresetRepository) {
        loadPersonas()
    }

    val activePersonas = personas.filter { it.isDefault }
    val otherPersonas = personas.filter { !it.isDefault }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
            ) {
                STTopHeader(
                    title = "扮演者",
                    leading = {
                        STIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                    },
                    actions = {
                        STIconButton(Icons.Filled.AutoFixHigh, "AI 生成", { onShowMessage("AI 生成扮演者功能开发中") })
                        STIconButton(Icons.Filled.Add, "新建", { onShowMessage("新建扮演者功能开发中") })
                    },
                    titleBottomPadding = 4.dp
                )
                Text(
                    text = "模型会用“你”扮演的身份来回应。可以为不同角色绑定不同扮演者。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )

                if (!running) {
                    STSystemInfoCard("本地服务未启动", "启动 SillyTavern 服务后可加载和管理扮演者设定。")
                } else if (personas.isEmpty()) {
                    STSystemInfoCard("暂无扮演者设定", "点击右下角“新建”以添加您在聊天中扮演的角色。")
                } else {
                    STSectionHeader("当前激活")
                    activePersonas.forEach { persona ->
                        STPersonaRow(persona = persona, active = true)
                    }

                    if (otherPersonas.isNotEmpty()) {
                        STSectionHeader("所有扮演者", trailing = {
                            Text("管理绑定", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        })
                        otherPersonas.forEach { persona ->
                            STPersonaRow(
                                persona = persona,
                                active = false,
                                onClick = {
                                    scope.launch {
                                        try {
                                            personaPresetRepository.savePersona(
                                                PersonaSaveRequest(
                                                    avatar = persona.avatar,
                                                    name = persona.name,
                                                    title = persona.title,
                                                    description = persona.description,
                                                    makeDefault = true
                                                )
                                            )
                                            onShowMessage("已切换默认扮演者：${persona.name}")
                                            loadPersonas()
                                        } catch (e: Exception) {
                                            onShowMessage(e.message ?: "切换扮演者失败")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(104.dp))
            }

            ExtendedFloatingActionButton(
                onClick = { onShowMessage("新建扮演者功能开发中") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("新建") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun STPersonaRow(
    persona: PersonaProfile,
    active: Boolean,
    onClick: (() -> Unit)? = null
) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = if (active) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
        border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            STAvatar(persona.name, size = 48.dp, ringColor = if (active) MaterialTheme.colorScheme.primary else null)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(persona.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (active) {
                        STBadge(
                            "当前",
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Text(
                    text = persona.description.ifBlank { persona.title.ifBlank { "未填写描述" } },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            IconButton(onClick = { }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
