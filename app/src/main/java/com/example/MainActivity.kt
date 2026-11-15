package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.model.ExportedClip
import com.example.model.StudioRoute
import com.example.ui.StudioViewModel
import com.example.ui.components.ExportCompleteDialog
import com.example.ui.components.ExportProgressDialog
import com.example.ui.components.VideoPlayerDialog
import com.example.ui.screens.AvatarsScreen
import com.example.ui.screens.ClipsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.VoxSculptTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoxSculptTheme {
                VoxSculptApp()
            }
        }
    }
}

private data class NavTabItem(
    val route: StudioRoute,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun VoxSculptApp(
    viewModel: StudioViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var activePlayerClip by remember { mutableStateOf<ExportedClip?>(null) }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeStatusMessage()
        }
    }

    LaunchedEffect(state.errorMessage) {
        val err = state.errorMessage
        if (err != null) {
            snackbarHostState.showSnackbar(err)
            viewModel.consumeErrorMessage()
        }
    }

    val navItems = remember {
        listOf(
            NavTabItem(
                route = StudioRoute.Studio,
                label = "Studio",
                selectedIcon = Icons.Filled.Videocam,
                unselectedIcon = Icons.Outlined.Videocam,
                testTag = "nav_studio"
            ),
            NavTabItem(
                route = StudioRoute.Avatars,
                label = "Avatars",
                selectedIcon = Icons.Filled.Face,
                unselectedIcon = Icons.Outlined.Face,
                testTag = "nav_avatars"
            ),
            NavTabItem(
                route = StudioRoute.Clips,
                label = "Clips",
                selectedIcon = Icons.Filled.Movie,
                unselectedIcon = Icons.Outlined.Movie,
                testTag = "nav_clips"
            )
        )
    }

    fun navigateTo(route: StudioRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = StudioSurface,
                        contentColor = TextPrimary
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    val icon = if (selected) item.selectedIcon else item.unselectedIcon
                                    if (item.route == StudioRoute.Clips && state.exportedClips.isNotEmpty()) {
                                        BadgedBox(
                                            badge = {
                                                Badge(
                                                    containerColor = ElectricCyan,
                                                    contentColor = ObsidianBg
                                                ) {
                                                    Text("${state.exportedClips.size}")
                                                }
                                            }
                                        ) {
                                            Icon(icon, contentDescription = item.label)
                                        }
                                    } else {
                                        Icon(icon, contentDescription = item.label)
                                    }
                                },
                                label = {
                                    Text(item.label, style = MaterialTheme.typography.labelLarge)
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = ElectricCyan.copy(alpha = 0.16f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = StudioSurface,
                        contentColor = TextPrimary
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = ElectricCyan.copy(alpha = 0.16f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = StudioRoute.Studio,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<StudioRoute.Studio> {
                        StudioScreen(
                            state = state,
                            onSelectInputTab = viewModel::selectInputTab,
                            onStartLiveMic = viewModel::startLiveMic,
                            onStopLiveMic = viewModel::stopLiveMic,
                            onImportAudioUri = viewModel::importAudioUri,
                            onSynthesizeScript = viewModel::synthesizeScript,
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onSeekAudio = viewModel::seekAudio,
                            onExportVideo = { viewModel.exportVideoClip() },
                            onResetCamera = viewModel::resetCamera,
                            onFpsUpdate = viewModel::updateViewportFps,
                            onSelectLighting = viewModel::setLightingPreset,
                            onSelectBackdrop = viewModel::setStageBackdrop,
                            onSelectAspectRatio = viewModel::setAspectRatio,
                            onNavigateToAvatars = { navigateTo(StudioRoute.Avatars) }
                        )
                    }

                    composable<StudioRoute.Avatars> {
                        AvatarsScreen(
                            state = state,
                            onSelectAvatar = {
                                viewModel.selectAvatar(it)
                                navigateTo(StudioRoute.Studio)
                            },
                            onImportAvatarUri = { uri ->
                                viewModel.importCustomAvatar(uri)
                                navigateTo(StudioRoute.Studio)
                            },
                            onDeleteCustomAvatar = viewModel::deleteCustomAvatar,
                            onSelectLighting = viewModel::setLightingPreset,
                            onSelectBackdrop = viewModel::setStageBackdrop
                        )
                    }

                    composable<StudioRoute.Clips> {
                        ClipsScreen(
                            clips = state.exportedClips,
                            onPlayClip = { activePlayerClip = it },
                            onShareClip = { shareMp4Clip(context, it) },
                            onDeleteClip = viewModel::deleteExportedClip,
                            onGoToStudio = { navigateTo(StudioRoute.Studio) }
                        )
                    }
                }
            }
        }
    }

    state.exportProgress?.let { prog ->
        ExportProgressDialog(
            progress = prog,
            onCancel = viewModel::cancelExport
        )
    }

    state.latestExportedClip?.let { exported ->
        ExportCompleteDialog(
            clip = exported,
            onPlayNow = {
                viewModel.dismissLatestExportedClip()
                activePlayerClip = exported
            },
            onShare = {
                shareMp4Clip(context, exported)
            },
            onDismiss = viewModel::dismissLatestExportedClip
        )
    }

    activePlayerClip?.let { clip ->
        VideoPlayerDialog(
            clip = clip,
            onShare = { shareMp4Clip(context, clip) },
            onDismiss = { activePlayerClip = null }
        )
    }
}

private fun shareMp4Clip(context: Context, clip: ExportedClip) {
    val file = File(clip.videoPath)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, clip.title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Lip-Synced Video"))
}
