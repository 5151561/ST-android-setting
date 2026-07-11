package io.github.sanitised.st

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.SolidColor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConfigScreen(
    onBack: () -> Unit,
    onOpenDocs: () -> Unit,
    canEdit: Boolean,
    configFile: File,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modeState = rememberSaveable { mutableStateOf("form") }
    val textState = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val originalState = rememberSaveable { mutableStateOf("") }
    val loadedState = rememberSaveable { mutableStateOf(false) }
    val missingState = rememberSaveable { mutableStateOf(false) }
    val isSavingState = remember { mutableStateOf(false) }
    val showDiscardDialog = rememberSaveable { mutableStateOf(false) }
    val formDocumentState = remember { mutableStateOf<ConfigFormDocument?>(null) }
    val formErrorState = remember { mutableStateOf<String?>(null) }
    val formValues = remember { mutableStateMapOf<String, String>() }
    val originalFormValuesState = remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun rebuildForm(content: String, resetValues: Boolean) {
        val result = runCatching { ConfigFormTools.parse(content) }
        formDocumentState.value = result.getOrNull()
        formErrorState.value = result.exceptionOrNull()?.message
        if (resetValues) {
            formValues.clear()
            result.getOrNull()?.initialValues?.let { formValues.putAll(it) }
            originalFormValuesState.value = formValues.toMap()
        }
    }

    val hasUnsavedText = textState.value.text != originalState.value
    val hasUnsavedForm = formValues.toMap() != originalFormValuesState.value
    val requestBack = {
        val canEditEffective = canEdit && !missingState.value
        if (canEditEffective && (hasUnsavedText || hasUnsavedForm)) {
            showDiscardDialog.value = true
        } else {
            onBack()
        }
    }

    LaunchedEffect(configFile.absolutePath) {
        val content = withContext(Dispatchers.IO) {
            if (configFile.exists()) configFile.readText(Charsets.UTF_8) else ""
        }
        textState.value = TextFieldValue(content, selection = TextRange(0))
        originalState.value = content
        missingState.value = !configFile.exists()
        loadedState.value = true
        rebuildForm(content, resetValues = true)
    }
    BackHandler(onBack = requestBack)

    fun switchToYaml() {
        if (modeState.value == "yaml") return
        val document = formDocumentState.value
        if (document != null && hasUnsavedForm) {
            val result = runCatching { ConfigFormTools.writeYaml(document, formValues.toMap()) }
            result
                .onSuccess { yaml ->
                    textState.value = TextFieldValue(yaml, selection = TextRange(yaml.length))
                    rebuildForm(yaml, resetValues = true)
                    modeState.value = "yaml"
                }
                .onFailure { error ->
                    formErrorState.value = error.message ?: context.getString(R.string.unknown_error)
                }
        } else {
            modeState.value = "yaml"
        }
    }

    fun switchToForm() {
        if (modeState.value == "form") return
        rebuildForm(textState.value.text, resetValues = true)
        modeState.value = "form"
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            SecondaryTopAppBar(
                title = stringResource(R.string.config_title),
                onBack = requestBack,
                actions = {
                    TextButton(onClick = onOpenDocs) {
                        Text(text = stringResource(R.string.docs))
                    }
                    ConfigModeAction(
                        mode = modeState.value,
                        onToggle = {
                            if (modeState.value == "form") {
                                switchToYaml()
                            } else {
                                switchToForm()
                            }
                        }
                    )
                }
            )

            val canEditEffective = canEdit && !missingState.value
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (missingState.value) {
                    Text(
                        text = stringResource(R.string.config_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text(
                    text = stringResource(R.string.config_file_name),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                ) {
                    if (!loadedState.value) {
                        Text(
                            text = stringResource(R.string.loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(4.dp)
                        )
                    } else if (modeState.value == "form") {
                        ConfigFormEditor(
                            document = formDocumentState.value,
                            error = formErrorState.value,
                            values = formValues,
                            canEdit = canEditEffective,
                            onValueChange = { key, value -> formValues[key] = value },
                            onOpenYaml = { switchToYaml() }
                        )
                    } else {
                        ConfigYamlEditor(
                            value = textState.value,
                            canEdit = canEditEffective,
                            onValueChange = { value ->
                                textState.value = value
                                rebuildForm(value.text, resetValues = false)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                if (!missingState.value && !canEdit) {
                    Text(
                        text = stringResource(R.string.config_stop_server),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Button(
                    onClick = {
                        isSavingState.value = true
                        scope.launch(Dispatchers.IO) {
                            val result = runCatching {
                                val contentToSave = if (modeState.value == "form") {
                                    val document = formDocumentState.value
                                        ?: error(formErrorState.value ?: context.getString(R.string.unknown_error))
                                    ConfigFormTools.writeYaml(document, formValues.toMap())
                                } else {
                                    textState.value.text
                                }
                                configFile.parentFile?.mkdirs()
                                configFile.writeText(contentToSave, Charsets.UTF_8)
                                contentToSave
                            }
                            withContext(Dispatchers.Main) {
                                isSavingState.value = false
                                result.onSuccess { savedText ->
                                    textState.value = TextFieldValue(
                                        savedText,
                                        selection = TextRange(savedText.length)
                                    )
                                    originalState.value = savedText
                                    rebuildForm(savedText, resetValues = true)
                                    onShowMessage(context.getString(R.string.config_saved))
                                }.onFailure { error ->
                                    onShowMessage(
                                        context.getString(
                                            R.string.config_save_failed,
                                            error.message ?: context.getString(R.string.unknown_error)
                                        )
                                    )
                                }
                            }
                        }
                    },
                    enabled = canEditEffective && loadedState.value && !isSavingState.value,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = if (isSavingState.value) stringResource(R.string.saving) else stringResource(R.string.save))
                }
            }
        }
    }

    if (showDiscardDialog.value) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog.value = false },
            title = { Text(text = stringResource(R.string.config_discard_title)) },
            text = { Text(text = stringResource(R.string.config_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog.value = false
                    onBack()
                }) {
                    Text(text = stringResource(R.string.discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog.value = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ConfigModeAction(
    mode: String,
    onToggle: () -> Unit
) {
    TextButton(onClick = onToggle) {
        Text(
            text = if (mode == "form") {
                stringResource(R.string.config_mode_yaml)
            } else {
                stringResource(R.string.config_mode_form)
            }
        )
    }
}

@Composable
private fun ConfigFormEditor(
    document: ConfigFormDocument?,
    error: String?,
    values: Map<String, String>,
    canEdit: Boolean,
    onValueChange: (String, String) -> Unit,
    onOpenYaml: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scrollbarColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollbar(scrollState, scrollbarColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(end = 8.dp)
        ) {
            if (document == null) {
                Text(
                    text = stringResource(
                        R.string.config_parse_failed,
                        error ?: stringResource(R.string.unknown_error)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenYaml) {
                    Text(stringResource(R.string.config_mode_yaml))
                }
                return@Column
            }

            document.rows.forEach { row ->
                when (row.kind) {
                    ConfigFormRowKind.SECTION -> ConfigSectionHeader(row)
                    ConfigFormRowKind.BOOLEAN -> ConfigBooleanRow(
                        row = row,
                        value = values[row.pathKey].orEmpty(),
                        canEdit = canEdit,
                        onValueChange = { onValueChange(row.pathKey, it) }
                    )
                    ConfigFormRowKind.NUMBER,
                    ConfigFormRowKind.TEXT,
                    ConfigFormRowKind.MULTILINE_TEXT,
                    ConfigFormRowKind.NULL,
                    ConfigFormRowKind.SCALAR_LIST,
                    ConfigFormRowKind.YAML -> ConfigFieldRow(
                        row = row,
                        value = values[row.pathKey].orEmpty(),
                        canEdit = canEdit,
                        onValueChange = { onValueChange(row.pathKey, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigSectionHeader(row: ConfigFormRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (row.depth * 12).dp, top = 14.dp, bottom = 6.dp)
    ) {
        HorizontalDivider()
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = row.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ConfigBooleanRow(
    row: ConfigFormRow,
    value: String,
    canEdit: Boolean,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (row.depth * 12).dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Switch(
            checked = value.equals("true", ignoreCase = true),
            onCheckedChange = { checked -> onValueChange(checked.toString()) },
            enabled = canEdit
        )
    }
}

@Composable
private fun ConfigFieldRow(
    row: ConfigFormRow,
    value: String,
    canEdit: Boolean,
    onValueChange: (String) -> Unit
) {
    val multiLine = row.kind == ConfigFormRowKind.MULTILINE_TEXT ||
        row.kind == ConfigFormRowKind.SCALAR_LIST ||
        row.kind == ConfigFormRowKind.YAML
    val keyboardType = KeyboardType.Text
    val isNumberError = row.kind == ConfigFormRowKind.NUMBER && value.trim().isBlank()
    val supportingText = fieldSupportingText(row)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (row.depth * 12).dp, top = 6.dp, bottom = 8.dp)
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        CompactConfigTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = canEdit,
            multiLine = multiLine,
            isError = isNumberError,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = keyboardType,
                imeAction = if (multiLine) ImeAction.Default else ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

@Composable
private fun CompactConfigTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    multiLine: Boolean,
    isError: Boolean,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    }
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f)
    }
    Surface(
        modifier = modifier.heightIn(min = if (multiLine) 104.dp else 48.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = !multiLine,
            maxLines = if (multiLine) 8 else 1,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = textColor,
                fontSize = 15.sp,
                lineHeight = 20.sp
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = keyboardOptions,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (multiLine) 10.dp else 12.dp)
        )
    }
}

@Composable
private fun fieldSupportingText(row: ConfigFormRow): String? {
    return when (row.kind) {
        ConfigFormRowKind.SCALAR_LIST -> stringResource(R.string.config_list_hint)
        ConfigFormRowKind.YAML -> stringResource(R.string.config_yaml_field_hint)
        ConfigFormRowKind.NULL -> stringResource(R.string.config_null_hint)
        else -> null
    }
}

@Composable
private fun ConfigYamlEditor(
    value: TextFieldValue,
    canEdit: Boolean,
    onValueChange: (TextFieldValue) -> Unit
) {
    val scrollState = rememberScrollState()
    val scrollbarColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.config_yaml_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScrollbar(scrollState, scrollbarColor)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { if (canEdit) onValueChange(it) },
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    readOnly = !canEdit,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Default
                    )
                )
            }
        }
    }
}
