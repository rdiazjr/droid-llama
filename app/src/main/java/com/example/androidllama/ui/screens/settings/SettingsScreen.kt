package com.example.androidllama.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.androidllama.data.settings.AppThemeMode
import com.example.androidllama.data.settings.PersonalityPreset
import com.example.androidllama.data.settings.ReasoningMode
import com.example.androidllama.data.settings.RuntimeBackend
import com.example.androidllama.ui.screens.settings.components.LabeledSlider
import com.example.androidllama.ui.screens.settings.components.SettingsSection
import com.example.androidllama.ui.screens.settings.components.SettingsSwitch
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val settings = viewModel.settings

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI settings", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Tune local inference and how the assistant responds.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = viewModel::reset) { Text("Reset") }
            }
        }

        item {
            SettingsSection(
                title = "Display",
                description = "Choose the app appearance or follow the device theme."
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
        }

        item {
            SettingsSection(
                title = "Runtime",
                description = "Choose where inference runs. Availability depends on the device and native model runtime."
            ) {
                Text("Compute backend", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RuntimeBackend.entries.forEach { backend ->
                        FilterChip(
                            selected = settings.backend == backend,
                            enabled = backend in viewModel.supportedBackends,
                            onClick = { viewModel.setBackend(backend) },
                            label = { Text(backend.label) }
                        )
                    }
                }
                Text(
                    "Detected backend devices",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
                RuntimeBackend.entries.forEach { backend ->
                    val devices = viewModel.backendDevices.filter { it.backend == backend }
                    val available = backend in viewModel.supportedBackends
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(backend.label, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    if (available) "Available" else "Unavailable",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (available) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                            if (devices.isNotEmpty()) {
                                devices.forEach { device ->
                                    val details = buildList {
                                        device.description.takeIf(String::isNotBlank)?.let(::add)
                                        device.totalMemoryBytes?.let { add(formatDeviceMemory(it)) }
                                    }.joinToString(" • ")
                                    Text(
                                        text = "• ${device.name}" +
                                            details.takeIf(String::isNotBlank)?.let { " — $it" }.orEmpty(),
                                        modifier = Modifier.padding(top = 3.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Text(
                                    viewModel.unavailableBackendReason(backend),
                                    modifier = Modifier.padding(top = 3.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Text(
                    settings.backend.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LabeledSlider(
                    label = "CPU threads",
                    valueLabel = settings.cpuThreads.toString(),
                    value = settings.cpuThreads.toFloat(),
                    valueRange = 1f..32f,
                    steps = 30,
                    onValueChange = { viewModel.setCpuThreads(it.roundToInt()) }
                )
                LabeledSlider(
                    label = "GPU offload layers",
                    valueLabel = settings.gpuLayers.toString(),
                    value = settings.gpuLayers.toFloat(),
                    valueRange = 0f..100f,
                    steps = 99,
                    enabled = settings.backend != RuntimeBackend.CPU,
                    onValueChange = { viewModel.setGpuLayers(it.roundToInt()) }
                )
                SettingsSwitch(
                    title = "Memory-map model",
                    description = "Reduce initial RAM copies when supported.",
                    checked = settings.useMemoryMapping,
                    onCheckedChange = viewModel::setMemoryMapping
                )
            }
        }

        item {
            SettingsSection(
                title = "Generation",
                description = "Control response length, creativity, and sampling behavior."
            ) {
                SettingsSwitch(
                    title = "Use model defaults",
                    description = "Apply recommended sampling settings embedded in the loaded GGUF when available.",
                    checked = settings.useModelDefaults,
                    onCheckedChange = viewModel::setUseModelDefaults
                )
                Text(
                    "Reasoning",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
                FlowRow(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ReasoningMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.reasoningMode == mode,
                            onClick = { viewModel.setReasoningMode(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
                Text(
                    settings.reasoningMode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                )
                LabeledSlider(
                    label = "Temperature",
                    valueLabel = settings.temperature.format(2),
                    value = settings.temperature,
                    valueRange = 0f..2f,
                    steps = 19,
                    onValueChange = viewModel::setTemperature
                )
                LabeledSlider(
                    label = "Top P",
                    valueLabel = settings.topP.format(2),
                    value = settings.topP,
                    valueRange = 0.1f..1f,
                    steps = 17,
                    onValueChange = viewModel::setTopP
                )
                LabeledSlider(
                    label = "Top K",
                    valueLabel = settings.topK.toString(),
                    value = settings.topK.toFloat(),
                    valueRange = 1f..100f,
                    steps = 98,
                    onValueChange = { viewModel.setTopK(it.roundToInt()) }
                )
                LabeledSlider(
                    label = "Min P",
                    valueLabel = settings.minP.format(2),
                    value = settings.minP,
                    valueRange = 0f..1f,
                    steps = 19,
                    onValueChange = viewModel::setMinP
                )
                LabeledSlider(
                    label = "Repeat penalty",
                    valueLabel = settings.repeatPenalty.format(2),
                    value = settings.repeatPenalty,
                    valueRange = 0.8f..1.5f,
                    steps = 13,
                    onValueChange = viewModel::setRepeatPenalty
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumericSettingField(
                        label = "Max output tokens",
                        value = settings.maxTokens,
                        modifier = Modifier.weight(1f),
                        onValueChange = viewModel::setMaxTokens
                    )
                    ContextWindowDropdown(
                        selected = settings.contextWindow,
                        modifier = Modifier.weight(1f),
                        onSelected = viewModel::setContextWindow
                    )
                }

                NumericSettingField(
                    label = "Seed (-1 = random)",
                    value = settings.seed,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    allowNegative = true,
                    onValueChange = viewModel::setSeed
                )
            }
        }

        item {
            SettingsSection(
                title = "Personality",
                description = "Define the tone and instructions applied before each conversation."
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PersonalityPreset.entries.forEach { personality ->
                        FilterChip(
                            selected = settings.personality == personality,
                            onClick = { viewModel.setPersonality(personality) },
                            label = { Text(personality.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = settings.systemPrompt,
                    onValueChange = viewModel::setSystemPrompt,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    label = { Text("How should the AI answer?") },
                    supportingText = { Text("${settings.systemPrompt.length} / 4000") },
                    minLines = 4,
                    maxLines = 10
                )
            }
        }

        item {
            SettingsSection(
                title = "Behavior",
                description = "Additional conversation and retrieval preferences."
            ) {
                SettingsSwitch(
                    title = "Stream responses",
                    description = "Display generated tokens as they arrive.",
                    checked = settings.streamResponses,
                    onCheckedChange = viewModel::setStreamResponses
                )
                SettingsSwitch(
                    title = "Remember conversation",
                    description = "Include prior messages within the context window.",
                    checked = settings.rememberConversation,
                    onCheckedChange = viewModel::setRememberConversation
                )
                SettingsSwitch(
                    title = "RAG citations",
                    description = "Ask the model to identify retrieved document sources.",
                    checked = settings.includeRagCitations,
                    onCheckedChange = viewModel::setRagCitations
                )
            }
        }

        item {
            Text(
                "Backend, GPU offload, and memory mapping take effect when the model is loaded again. " +
                    "Generation settings apply to the next response.",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    "Developed by Rogelio Diaz Jr.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NumericSettingField(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    allowNegative: Boolean = false,
    onValueChange: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            if (input.isEmpty() || (allowNegative && input == "-")) {
                text = input
            } else {
                input.toIntOrNull()?.let {
                    text = input
                    onValueChange(it)
                }
            }
        },
        modifier = modifier,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

@Composable
private fun ContextWindowDropdown(
    selected: Int,
    modifier: Modifier = Modifier,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val values = listOf(2048, 4096, 8192, 16384, 32768)
    Box(modifier = modifier) {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = true }
        ) {
            Text("Context: ${selected / 1024}K", modifier = Modifier.weight(1f))
            Icon(Icons.Default.ExpandMore, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 180.dp)
        ) {
            values.forEach { size ->
                DropdownMenuItem(
                    text = { Text("${size / 1024}K tokens") },
                    onClick = {
                        onSelected(size)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun Float.format(decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", this)

private fun formatDeviceMemory(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(
        Locale.US,
        "%.1f GB",
        bytes / (1024.0 * 1024.0 * 1024.0)
    )
    else -> String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
}
