/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import androidx.compose.runtime.Composable
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.ui.screens.settings.SettingsNavGraph

/**
 * SettingsScreen
 * Host entry point delegating to SettingsNavGraph for true 3-tier deep navigation.
 */
@Composable
fun SettingsScreen(
    repository: NexoraStreamRepository,
    onBack: () -> Unit
) {
    SettingsNavGraph(
        repository = repository,
        onBackToApp = onBack
    )
}
