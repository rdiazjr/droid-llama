package com.example.androidllama.ui.screens.models.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.androidllama.ui.screens.models.models.ModelInfo
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val BYTES_PER_MIB = 1024L * 1024L

private enum class ModelSort(val label: String) {
    TRENDING("Trending"),
    NAME("Name"),
    PARAMETERS("Parameters"),
    FILE_SIZE("File size")
}

private enum class ModelTab(val label: String) {
    DOWNLOADED("Downloaded"),
    DOWNLOADING("Pending"),
    ALL("All")
}

@Composable
fun ModelList(
    models: List<ModelInfo>,
    onDownloadClick: (ModelInfo) -> Unit,
    onLoadClick: (ModelInfo) -> Unit,
    onDeleteClick: (ModelInfo) -> Unit,
    onCancelDownload: (ModelInfo) -> Unit,
    isRefreshing: Boolean,
    isSearching: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    currentPage: Int,
    hasPreviousPage: Boolean,
    hasNextPage: Boolean,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onFiltersChanged: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filterExpanded by rememberSaveable { mutableStateOf(false) }
    var maximumSizeMb by rememberSaveable { mutableStateOf<Int?>(null) }
    var maximumParameterBucket by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedTags by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var sortName by rememberSaveable { mutableStateOf(ModelSort.TRENDING.name) }
    var sortAscending by rememberSaveable { mutableStateOf(true) }
    var selectedTabName by rememberSaveable { mutableStateOf(ModelTab.DOWNLOADED.name) }

    val availableTags = models.flatMap { it.tags }.distinct().sortedBy { it.lowercase() }
    val validParameterBuckets = models.map { it.parameterBucket }.distinct().sorted()
    val largestFileSizeMb = models.mapNotNull { it.sizeBytes }
        .maxOrNull()
        ?.let { ceil(it.toDouble() / BYTES_PER_MIB).toInt() }
        ?.coerceAtLeast(1)
        ?: 1
    val activeMaximumParameter = maximumParameterBucket
        ?.takeIf { it in validParameterBuckets }
        ?: validParameterBuckets.lastOrNull()
    val activeMaximumSizeMb = maximumSizeMb?.coerceIn(1, largestFileSizeMb) ?: largestFileSizeMb
    val selectedSort = ModelSort.entries.firstOrNull { it.name == sortName } ?: ModelSort.NAME
    val hasActiveFilters = maximumSizeMb != null || maximumParameterBucket != null ||
        selectedTags.isNotEmpty() || selectedSort != ModelSort.TRENDING || !sortAscending

    LaunchedEffect(availableTags) {
        selectedTags = selectedTags.filter { it in availableTags }
    }
    LaunchedEffect(validParameterBuckets) {
        if (maximumParameterBucket !in validParameterBuckets) maximumParameterBucket = null
    }
    LaunchedEffect(largestFileSizeMb) {
        maximumSizeMb = maximumSizeMb?.coerceAtMost(largestFileSizeMb)
    }

    val query = searchQuery.trim()
    val filteredModels = models.filter { model ->
        val matchesSearch = query.isEmpty() ||
            model.name.contains(query, ignoreCase = true) ||
            model.fileName.contains(query, ignoreCase = true) ||
            model.size.contains(query, ignoreCase = true) ||
            model.quantization.contains(query, ignoreCase = true) ||
            model.tags.any { it.contains(query, ignoreCase = true) }
        val matchesParameters = maximumParameterBucket == null ||
            model.parameterBucket <= maximumParameterBucket!!
        val matchesSize = maximumSizeMb == null ||
            model.sizeBytes?.let { it <= maximumSizeMb!!.toLong() * BYTES_PER_MIB } == true
        val matchesTags = selectedTags.isEmpty() ||
            model.tags.any { tag -> selectedTags.any { it.equals(tag, ignoreCase = true) } }
        matchesSearch && matchesParameters && matchesSize && matchesTags
    }
    val catalogOrder = models.mapIndexed { index, model -> model.id to index }.toMap()
    val comparator = when (selectedSort) {
        ModelSort.TRENDING -> compareBy<ModelInfo> { catalogOrder[it.id] ?: Int.MAX_VALUE }
        ModelSort.NAME -> compareBy<ModelInfo> { it.name.lowercase() }.thenBy { it.fileName.lowercase() }
        ModelSort.PARAMETERS -> compareBy<ModelInfo> { it.parameterCount }.thenBy { it.name.lowercase() }
        ModelSort.FILE_SIZE -> compareBy<ModelInfo> { it.sizeBytes ?: Long.MAX_VALUE }.thenBy { it.name.lowercase() }
    }.let { if (sortAscending) it else it.reversed() }
    val sortedModels = filteredModels.sortedWith(
        compareByDescending<ModelInfo> { it.loaded }.then(comparator)
    )
    val selectedTab = ModelTab.entries.firstOrNull { it.name == selectedTabName }
        ?: ModelTab.DOWNLOADED
    val tabModels = when (selectedTab) {
        ModelTab.DOWNLOADED -> sortedModels.filter { it.downloaded }
        ModelTab.DOWNLOADING -> sortedModels.filter { it.downloading }
        ModelTab.ALL -> sortedModels
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                onSearchQueryChanged(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search models") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            onSearchQueryChanged("")
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                    Box {
                        IconButton(onClick = { filterExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter models",
                                tint = if (hasActiveFilters) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        ModelFilterMenu(
                            expanded = filterExpanded,
                            onDismiss = { filterExpanded = false },
                            availableTags = availableTags,
                            selectedTags = selectedTags,
                            onTagChecked = { tag, checked ->
                                selectedTags = if (checked) {
                                    (selectedTags + tag).distinct()
                                } else {
                                    selectedTags - tag
                                }
                                onFiltersChanged()
                            },
                            validParameterBuckets = validParameterBuckets,
                            activeMaximumParameter = activeMaximumParameter,
                            onMaximumParameterChanged = {
                                maximumParameterBucket = it
                                onFiltersChanged()
                            },
                            largestFileSizeMb = largestFileSizeMb,
                            activeMaximumSizeMb = activeMaximumSizeMb,
                            onMaximumSizeChanged = {
                                maximumSizeMb = it
                                onFiltersChanged()
                            },
                            selectedSort = selectedSort,
                            onSortChanged = {
                                sortName = it.name
                                onFiltersChanged()
                            },
                            sortAscending = sortAscending,
                            onSortDirectionChanged = {
                                sortAscending = it
                                onFiltersChanged()
                            },
                            onReset = {
                                maximumSizeMb = null
                                maximumParameterBucket = null
                                selectedTags = emptyList()
                                sortName = ModelSort.TRENDING.name
                                sortAscending = true
                                onFiltersChanged()
                            }
                        )
                    }
                }
            },
            singleLine = true
        )

        PrimaryTabRow(selectedTabIndex = ModelTab.entries.indexOf(selectedTab)) {
            ModelTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTabName = tab.name },
                    text = {
                        Text(
                            text = if (tab == ModelTab.DOWNLOADED) "Down\u200Bloaded" else tab.label,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Visible,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                isSearching -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                "Searching all models…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                isRefreshing && models.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                error != null && models.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onRefresh) { Text("Retry") }
                        }
                    }
                }
                tabModels.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (filteredModels.isEmpty()) "No models match these filters"
                            else "No models in ${selectedTab.label}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(tabModels, key = { "${selectedTab.name}:${it.id}" }) { model ->
                            ModelListItem(
                                model = model,
                                onDownloadClick = { onDownloadClick(model) },
                                onLoadClick = { onLoadClick(model) },
                                onDeleteClick = { onDeleteClick(model) },
                                onCancelDownload = { onCancelDownload(model) }
                            )
                        }
                    }
                }
            }
        }

        if (selectedTab == ModelTab.ALL) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onPreviousPage,
                    enabled = hasPreviousPage && !isRefreshing && !isSearching
                ) {
                    Text("Previous")
                }
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.width(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Page $currentPage", style = MaterialTheme.typography.labelLarge)
                }
                TextButton(
                    onClick = onNextPage,
                    enabled = hasNextPage && !isRefreshing && !isSearching
                ) {
                    Text("Next")
                }
            }
        }
    }
}

@Composable
private fun ModelFilterMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    availableTags: List<String>,
    selectedTags: List<String>,
    onTagChecked: (String, Boolean) -> Unit,
    validParameterBuckets: List<Int>,
    activeMaximumParameter: Int?,
    onMaximumParameterChanged: (Int) -> Unit,
    largestFileSizeMb: Int,
    activeMaximumSizeMb: Int,
    onMaximumSizeChanged: (Int) -> Unit,
    selectedSort: ModelSort,
    onSortChanged: (ModelSort) -> Unit,
    sortAscending: Boolean,
    onSortDirectionChanged: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .width(300.dp)
            .heightIn(max = 360.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text("Parameters", style = MaterialTheme.typography.titleSmall)
            Text(
                activeMaximumParameter?.let { "Up to ${it}B" } ?: "No parameter options",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (validParameterBuckets.isNotEmpty()) {
                val selectedIndex = validParameterBuckets.indexOf(activeMaximumParameter)
                    .coerceAtLeast(0)
                Slider(
                    value = selectedIndex.toFloat(),
                    onValueChange = { index ->
                        onMaximumParameterChanged(validParameterBuckets[index.roundToInt()])
                    },
                    valueRange = 0f..validParameterBuckets.lastIndex.coerceAtLeast(1).toFloat(),
                    steps = (validParameterBuckets.size - 2).coerceAtLeast(0),
                    enabled = validParameterBuckets.size > 1
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    validParameterBuckets.forEach { Text("${it}B", style = MaterialTheme.typography.labelSmall) }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            Text("File size", style = MaterialTheme.typography.titleSmall)
            Text(
                "Up to $activeMaximumSizeMb MB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = activeMaximumSizeMb.toFloat(),
                onValueChange = { onMaximumSizeChanged(it.roundToInt().coerceAtLeast(1)) },
                valueRange = 1f..largestFileSizeMb.coerceAtLeast(2).toFloat()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Sort by",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall
                )
                IconButton(onClick = { onSortDirectionChanged(!sortAscending) }) {
                    Icon(
                        imageVector = if (sortAscending) {
                            Icons.Default.ArrowUpward
                        } else {
                            Icons.Default.ArrowDownward
                        },
                        contentDescription = if (sortAscending) {
                            "Sort descending"
                        } else {
                            "Sort ascending"
                        }
                    )
                }
            }
            ModelSort.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSortChanged(option) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedSort == option,
                        onClick = { onSortChanged(option) }
                    )
                    Text(option.label, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (availableTags.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                Text("Tags", style = MaterialTheme.typography.titleSmall)
                Column(modifier = Modifier.fillMaxWidth()) {
                    availableTags.forEach { tag ->
                        val checked = tag in selectedTags
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTagChecked(tag, !checked) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onTagChecked(tag, it) }
                            )
                            Text(tag, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onReset) { Text("Reset filters") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}
