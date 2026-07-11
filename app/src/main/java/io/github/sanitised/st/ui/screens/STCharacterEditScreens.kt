package io.github.sanitised.st.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.CharacterSaveRequest
import io.github.sanitised.st.api.TavernCoreClient
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// P0 · 角色完整编辑（设计稿 screens/CharEditFull.jsx，画板 07–09）
// 接真实后端：getCharacter / updateCharacter。每屏拉取完整角色，编辑各自字段后
// 组完整 CharacterSaveRequest 写回（含 rawJsonData 兜底未知字段），不丢其它字段。
// ─────────────────────────────────────────────────────────────────────────────

private fun CharacterDetail.toSaveRequest(avatarOverride: String): CharacterSaveRequest = CharacterSaveRequest(
    avatar = avatarOverride,
    name = name,
    description = description,
    personality = personality,
    scenario = scenario,
    firstMessage = firstMessage,
    messageExample = messageExample,
    creatorNotes = creatorNotes,
    systemPrompt = systemPrompt,
    postHistoryInstructions = postHistoryInstructions,
    tags = tags,
    creator = creator,
    characterVersion = characterVersion,
    world = world,
    talkativeness = talkativeness,
    isFavorite = isFavorite,
    alternateGreetings = alternateGreetings,
    depthPrompt = depthPrompt,
    depthPromptDepth = depthPromptDepth,
    depthPromptRole = depthPromptRole,
    chat = chat,
    createDate = createDate,
    rawJsonData = rawJsonData,
    sourceUrl = sourceUrl,
)

private fun commaList(text: String): List<String> =
    text.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }

// ── 07 完整编辑表单 ──────────────────────────────────────────────────────────
@Composable
fun STCharacterFormScreen(
    avatar: String,
    status: NodeStatus,
    baseUrl: String,
    onClose: () -> Unit,
    onOpenGreetings: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onShowMessage: (String) -> Unit = {},
) {
    BackHandler(onBack = onClose)
    val running = status.state == NodeState.RUNNING
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<CharacterDetail?>(null) }

    var name by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var creator by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var firstMessage by remember { mutableStateOf("") }
    var example by remember { mutableStateOf("") }

    LaunchedEffect(running, baseUrl, avatar) {
        if (!running) { loading = false; return@LaunchedEffect }
        loading = true
        runCatching { TavernCoreClient(baseUrl).getCharacter(avatar) }
            .onSuccess { d ->
                detail = d
                name = d.name; tags = d.tags.joinToString(", "); creator = d.creator; version = d.characterVersion
                notes = d.creatorNotes; description = d.description; firstMessage = d.firstMessage; example = d.messageExample
            }
            .onFailure { onShowMessage(it.message ?: "角色加载失败") }
        loading = false
    }

    fun save() {
        val d = detail ?: return
        scope.launch {
            runCatching {
                TavernCoreClient(baseUrl).updateCharacter(
                    d.toSaveRequest(avatar).copy(
                        name = name, tags = commaList(tags), creator = creator, characterVersion = version,
                        creatorNotes = notes, description = description, firstMessage = firstMessage, messageExample = example
                    )
                )
            }.onSuccess { onShowMessage("角色已保存"); onClose() }.onFailure { onShowMessage(it.message ?: "保存失败") }
        }
    }

    P0Scaffold(
        title = "编辑角色",
        subtitle = detail?.let { "${it.name} · ${it.characterVersion.ifBlank { "v1" }}" } ?: "",
        onBack = onClose,
        closeIcon = true,
        actions = { STIconButton(Icons.Filled.Check, "保存", { save() }) }
    ) {
        if (!running) { CharOffline(); return@P0Scaffold }
        if (loading) { CharLoading(); return@P0Scaffold }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box {
                STAvatar(name.ifBlank { "角色" }, size = 96.dp, square = true, imageUrl = detail?.avatarUrl, baseUrl = baseUrl, gradient = stGradientFor(avatar.hashCode()))
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).size(32.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = "更换头像", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                P0Field(label = "角色名", value = name, onValueChange = { name = it })
            }
        }

        P0Field(label = "标签（逗号分隔）", value = tags, onValueChange = { tags = it })
        Row(Modifier.fillMaxWidth()) {
            P0Field(label = "创作者", value = creator, onValueChange = { creator = it }, modifier = Modifier.weight(1f))
            P0Field(label = "角色版本", value = version, onValueChange = { version = it }, modifier = Modifier.width(140.dp))
        }
        P0Field(label = "创作者笔记", value = notes, onValueChange = { notes = it }, multiline = true, minLines = 2, hint = "展示在角色列表里，不进入提示词")
        P0Field(label = "描述", value = description, onValueChange = { description = it }, multiline = true, minLines = 6, hint = "支持 {{char}} / {{user}} 宏")
        P0Field(label = "开场白", value = firstMessage, onValueChange = { firstMessage = it }, multiline = true, minLines = 2)
        STListItem(
            headline = "备选开场白",
            supporting = "${detail?.alternateGreetings?.size ?: 0} 个 · 新聊天第一条消息的滑动项",
            leading = { CharLead(Icons.Filled.AutoStories) },
            trailing = { CharChevron() },
            onClick = onOpenGreetings
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        P0Field(label = "对话示例", value = example, onValueChange = { example = it }, multiline = true, minLines = 5, mono = true, hint = "每段示例以 <START> 开头")

        P0SectionHeader("绑定")
        STListItem(
            headline = "世界书",
            supporting = detail?.world?.ifBlank { "未绑定" } ?: "未绑定",
            leading = { CharLead(Icons.Filled.AutoStories) },
            trailing = { CharChevron() },
            onClick = { onShowMessage("绑定世界书：在世界书页设置") }
        )
        STListItem(
            headline = "高级定义",
            supporting = "Prompt 覆盖 · 角色备注 · 健谈度",
            leading = { CharLead(Icons.Filled.Tune) },
            trailing = { CharChevron() },
            onClick = onOpenAdvanced
        )
        if (!detail?.sourceUrl.isNullOrBlank()) {
            STListItem(
                headline = "角色源",
                supporting = detail?.sourceUrl ?: "",
                leading = { CharLead(Icons.Filled.Link) },
                onClick = { onShowMessage("角色源：${detail?.sourceUrl}") }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ── 08 备选开场白 ────────────────────────────────────────────────────────────
@Composable
fun STAltGreetingsScreen(
    avatar: String,
    status: NodeStatus,
    baseUrl: String,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val running = status.state == NodeState.RUNNING
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<CharacterDetail?>(null) }
    var mainGreeting by remember { mutableStateOf("") }
    val greetings = remember { mutableStateListOf<String>() }

    LaunchedEffect(running, baseUrl, avatar) {
        if (!running) { loading = false; return@LaunchedEffect }
        loading = true
        runCatching { TavernCoreClient(baseUrl).getCharacter(avatar) }
            .onSuccess { d -> detail = d; mainGreeting = d.firstMessage; greetings.clear(); greetings.addAll(d.alternateGreetings) }
            .onFailure { onShowMessage(it.message ?: "角色加载失败") }
        loading = false
    }

    fun save() {
        val d = detail ?: return
        scope.launch {
            runCatching {
                TavernCoreClient(baseUrl).updateCharacter(
                    d.toSaveRequest(avatar).copy(firstMessage = mainGreeting, alternateGreetings = greetings.toList())
                )
            }.onSuccess { onShowMessage("已保存"); onBack() }.onFailure { onShowMessage(it.message ?: "保存失败") }
        }
    }

    P0Scaffold(
        title = "备选开场白",
        subtitle = detail?.name ?: "",
        onBack = onBack,
        actions = {
            STIconButton(Icons.Filled.Add, "添加", { greetings.add("") })
            STIconButton(Icons.Filled.Check, "保存", { save() })
        }
    ) {
        if (!running) { CharOffline(); return@P0Scaffold }
        if (loading) { CharLoading(); return@P0Scaffold }
        P0InfoBanner("备选开场白会成为新聊天第一条消息的滑动项。左右滑动开场消息即可切换。")
        GreetingEditor("主开场（滑动 1）", mainGreeting, onChange = { mainGreeting = it }, onDelete = null, main = true)
        greetings.forEachIndexed { index, g ->
            GreetingEditor(
                label = "备选 ${index + 1}（滑动 ${index + 2}）",
                text = g,
                onChange = { greetings[index] = it },
                onDelete = { greetings.removeAt(index) },
                main = false
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)
                .clip(RoundedCornerShape(16.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .clickable { greetings.add("") }.padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("添加备选开场白", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun GreetingEditor(label: String, text: String, onChange: (String) -> Unit, onDelete: (() -> Unit)?, main: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, if (main) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(bottom = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (main) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            if (onDelete != null) {
                Box(modifier = Modifier.size(36.dp).clickable { onDelete() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        }
        P0Field(label = "内容", value = text, onValueChange = onChange, multiline = true, minLines = 3)
    }
}

// ── 09 高级定义 ──────────────────────────────────────────────────────────────
@Composable
fun STCharacterAdvancedScreen(
    avatar: String,
    status: NodeStatus,
    baseUrl: String,
    onClose: () -> Unit,
    onShowMessage: (String) -> Unit = {},
) {
    BackHandler(onBack = onClose)
    val running = status.state == NodeState.RUNNING
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<CharacterDetail?>(null) }

    var systemPrompt by remember { mutableStateOf("") }
    var postHistory by remember { mutableStateOf("") }
    var depthPrompt by remember { mutableStateOf("") }
    var depthValue by remember { mutableIntStateOf(4) }
    var roleIndex by remember { mutableIntStateOf(0) }
    var personality by remember { mutableStateOf("") }
    var scenario by remember { mutableStateOf("") }
    var talkativeness by remember { mutableFloatStateOf(0.5f) }

    LaunchedEffect(running, baseUrl, avatar) {
        if (!running) { loading = false; return@LaunchedEffect }
        loading = true
        runCatching { TavernCoreClient(baseUrl).getCharacter(avatar) }
            .onSuccess { d ->
                detail = d
                systemPrompt = d.systemPrompt; postHistory = d.postHistoryInstructions; depthPrompt = d.depthPrompt
                depthValue = d.depthPromptDepth
                roleIndex = when (d.depthPromptRole) { "user" -> 1; "assistant" -> 2; else -> 0 }
                personality = d.personality; scenario = d.scenario; talkativeness = d.talkativeness.toFloat()
            }
            .onFailure { onShowMessage(it.message ?: "角色加载失败") }
        loading = false
    }

    fun save() {
        val d = detail ?: return
        scope.launch {
            runCatching {
                TavernCoreClient(baseUrl).updateCharacter(
                    d.toSaveRequest(avatar).copy(
                        systemPrompt = systemPrompt, postHistoryInstructions = postHistory,
                        depthPrompt = depthPrompt, depthPromptDepth = depthValue,
                        depthPromptRole = when (roleIndex) { 1 -> "user"; 2 -> "assistant"; else -> "system" },
                        personality = personality, scenario = scenario, talkativeness = talkativeness.toDouble()
                    )
                )
            }.onSuccess { onShowMessage("已保存"); onClose() }.onFailure { onShowMessage(it.message ?: "保存失败") }
        }
    }

    P0Scaffold(
        title = "高级定义",
        subtitle = detail?.name ?: "",
        onBack = onClose,
        closeIcon = true,
        actions = { STIconButton(Icons.Filled.Check, "保存", { save() }) }
    ) {
        if (!running) { CharOffline(); return@P0Scaffold }
        if (loading) { CharLoading(); return@P0Scaffold }

        P0SectionHeader("提示词覆盖")
        P0Field(label = "主提示词覆盖", value = systemPrompt, onValueChange = { systemPrompt = it }, multiline = true, minLines = 2, placeholder = "留空使用全局主提示词。写入 {{original}} 可引用全局值。")
        P0Field(label = "历史后指令（Post-History）", value = postHistory, onValueChange = { postHistory = it }, multiline = true, minLines = 2)

        P0SectionHeader("角色备注（Character Note）")
        P0Field(label = "备注内容", value = depthPrompt, onValueChange = { depthPrompt = it }, multiline = true, minLines = 3, hint = "按设定深度插入对话历史，角色不会「忘记」这些事实")
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            P0Stepper(label = "插入深度", value = depthValue, onValueChange = { depthValue = it }, modifier = Modifier.weight(1f), min = 0)
            Column(Modifier.weight(2f)) {
                Text("插入角色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                P0Seg(options = listOf("系统", "用户", "AI"), selectedIndex = roleIndex, onSelect = { roleIndex = it })
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        P0SectionHeader("性格与场景")
        P0Field(label = "性格概述", value = personality, onValueChange = { personality = it }, multiline = true, minLines = 2)
        P0Field(label = "场景（Scenario）", value = scenario, onValueChange = { scenario = it }, multiline = true, minLines = 2)
        P0Slider("健谈度（群聊中主动发言倾向）", value = talkativeness, onValueChange = { talkativeness = it }, valueRange = 0f..1f, valueText = { "${(it * 100).toInt()}%" })
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("沉默寡言", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("话痨", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        P0SectionHeader("元数据")
        STListItem(
            headline = "创建日期",
            supporting = detail?.createDate?.ifBlank { "未知" } ?: "未知",
            leading = { CharLead(Icons.Filled.Schedule) }
        )
        STListItem(
            headline = "规范格式",
            supporting = "Character Card V2 (chara_card_v2)",
            leading = { CharLead(Icons.Filled.Fingerprint) }
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CharLead(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CharChevron() {
    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CharOffline() {
    Text(
        "本地服务未启动。启动服务后可读写真实角色卡。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
private fun CharLoading() {
    Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text("正在读取角色…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
