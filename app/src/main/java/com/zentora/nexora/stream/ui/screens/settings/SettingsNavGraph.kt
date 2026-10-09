/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository

/**
 * SettingsNavGraph
 * Dedicated nested Jetpack Compose NavHost for 3-tier deep YouTube settings navigation:
 *
 * Level 1:
 *  - "settings/root" -> MainSettingsList
 *
 * Level 2:
 *  - "settings/general" -> GeneralSettingsScreen
 *  - "settings/account" -> AccountSettingsScreen
 *  - "settings/quality" -> VideoQualityPreferencesScreen
 *  - "settings/downloads" -> DownloadsSettingsScreen
 *
 * Level 3 (under "settings/general/..."):
 *  - "settings/general/account_security" -> AccountSecurityScreen
 *  - "settings/general/playback" -> PlaybackSettingsScreen
 *  - "settings/general/reminders" -> RemindersSettingsScreen
 */
@Composable
fun SettingsNavGraph(
    repository: NexoraStreamRepository,
    onBackToApp: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "settings/root"
    ) {
        // --- Level 1 ---
        composable("settings/root") {
            MainSettingsList(
                navController = navController,
                repository = repository,
                onBack = onBackToApp
            )
        }

        // --- Level 2 ---
        composable("settings/general") {
            GeneralSettingsScreen(
                navController = navController,
                repository = repository
            )
        }

        composable("settings/account") {
            AccountSettingsScreen(
                navController = navController,
                repository = repository
            )
        }

        composable("settings/quality") {
            VideoQualityPreferencesScreen(
                navController = navController
            )
        }

        composable("settings/downloads") {
            DownloadsSettingsScreen(
                navController = navController
            )
        }

        // --- Level 3 (Deep Child Leaf Pages) ---
        composable("settings/general/account_security") {
            AccountSecurityScreen(
                navController = navController
            )
        }

        composable("settings/general/playback") {
            PlaybackSettingsScreen(
                navController = navController
            )
        }

        composable("settings/general/reminders") {
            RemindersSettingsScreen(
                navController = navController
            )
        }
    }
}
