package com.lyihub.archiveassistant.ui.screens

import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

@Composable
internal fun MemorialImmersiveSystemUi(onDispose: () -> Unit) {
  val hostView = LocalView.current
  DisposableEffect(hostView) {
    val window = (hostView.context as? android.app.Activity)?.window
    window?.insetsController?.let { controller ->
      controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
      controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    onDispose {
      window
        ?.insetsController
        ?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
      onDispose()
    }
  }
}
