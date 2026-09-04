package com.example.androidllama.ui.screens.models

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.androidllama.ui.screens.models.components.ModelList
import com.example.androidllama.ui.screens.models.models.ModelViewModel

@Composable
fun ModelScreen(
    viewModel: ModelViewModel = viewModel()
) {
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    ModelList(
        models = viewModel.models,
        onDownloadClick = { model ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            viewModel.downloadModel(model)
        },
        onLoadClick = viewModel::loadModel,
        onDeleteClick = viewModel::deleteModel,
        onCancelDownload = viewModel::cancelDownload,
        isRefreshing = viewModel.isRefreshing,
        isSearching = viewModel.isSearching,
        error = viewModel.catalogError,
        onRefresh = viewModel::refreshModels,
        currentPage = viewModel.currentPage,
        hasPreviousPage = viewModel.hasPreviousPage,
        hasNextPage = viewModel.hasNextPage,
        onPreviousPage = viewModel::previousPage,
        onNextPage = viewModel::nextPage,
        onSearchQueryChanged = viewModel::searchModels,
        onFiltersChanged = viewModel::resetToFirstPageForFilters
    )
}
