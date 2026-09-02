package com.mozhi.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mozhi.feature.models.ui.ModelsRoute
import com.mozhi.feature.transcribe.ui.TranscribeRoute

object Routes {
    const val Transcribe = "transcribe"
    const val Models = "models"
}

@Composable
fun MozhiAppRoot() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.Transcribe) {
        composable(Routes.Transcribe) {
            TranscribeRoute(onOpenModels = { nav.navigate(Routes.Models) })
        }
        composable(Routes.Models) {
            ModelsRoute(onBack = { nav.popBackStack() })
        }
    }
}
