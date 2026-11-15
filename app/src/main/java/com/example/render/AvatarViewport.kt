package com.example.render

import android.opengl.GLSurfaceView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.avatar.AvatarModel3D
import com.example.model.FacialRigState
import com.example.model.LightingPreset
import com.example.model.StageBackdrop

@Composable
fun AvatarViewport(
    model: AvatarModel3D,
    rigState: FacialRigState,
    lightingPreset: LightingPreset,
    stageBackdrop: StageBackdrop,
    resetTrigger: Int,
    onFpsUpdate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val renderer = remember {
        AvatarGlRenderer(model).apply {
            this.onFpsUpdate = onFpsUpdate
        }
    }

    LaunchedEffect(model.id, model.totalVertices) {
        renderer.setModel(model)
    }

    LaunchedEffect(rigState) {
        renderer.rigState = rigState
    }

    LaunchedEffect(lightingPreset) {
        renderer.lightingPreset = lightingPreset
    }

    LaunchedEffect(stageBackdrop) {
        renderer.stageBackdrop = stageBackdrop
    }

    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) renderer.resetCamera()
    }

    val glSurfaceView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    DisposableEffect(glSurfaceView) {
        onDispose {
            glSurfaceView.queueEvent { renderer.release() }
        }
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    renderer.cameraYawDeg = (renderer.cameraYawDeg + pan.x * 0.25f)
                        .coerceIn(-75f, 75f)
                    renderer.cameraPitchDeg = (renderer.cameraPitchDeg + pan.y * 0.18f)
                        .coerceIn(-30f, 30f)
                    renderer.cameraZoom = (renderer.cameraZoom * zoom)
                        .coerceIn(0.65f, 1.75f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { renderer.resetCamera() }
                )
            }
    ) {
        AndroidView(
            factory = { glSurfaceView },
            modifier = Modifier.fillMaxSize(),
            onRelease = { view -> view.onPause() }
        )
    }
}
