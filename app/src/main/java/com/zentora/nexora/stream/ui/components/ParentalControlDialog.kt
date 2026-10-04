/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.zentora.nexora.stream.ui.theme.NexoraRed

/**
 * Feature 29: Parental Control & Kids Mode Dialog protected by 4-digit master PIN.
 */
@Composable
fun ParentalControlDialog(
    isCurrentlyKidsMode: Boolean,
    onDismiss: () -> Unit,
    onToggleKidsMode: (targetState: Boolean, pin: String) -> Boolean
) {
    val context = LocalContext.current
    var pinText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isCurrentlyKidsMode) Icons.Filled.Lock else Icons.Filled.ChildCare,
                    contentDescription = null,
                    tint = NexoraRed
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCurrentlyKidsMode) "Exit Kids Mode" else "Enter Kids Mode",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isCurrentlyKidsMode)
                        "Enter the 4-digit master PIN to unlock full Nexora Stream catalog."
                    else
                        "Kids Mode restricts content to family-safe and animation categories. Enter master PIN to activate (Default: 1234):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pinText,
                    onValueChange = {
                        if (it.length <= 4) pinText = it
                        errorMessage = null
                    },
                    label = { Text("4-Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage?.let { err ->
                    Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pinText.length != 4) {
                        errorMessage = "Please enter a 4-digit PIN"
                        return@Button
                    }
                    val target = !isCurrentlyKidsMode
                    val success = onToggleKidsMode(target, pinText)
                    if (success) {
                        Toast.makeText(
                            context,
                            if (target) "Kids Mode activated" else "Kids Mode disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                        onDismiss()
                    } else {
                        errorMessage = "Incorrect PIN. Default is 1234"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NexoraRed)
            ) {
                Text(if (isCurrentlyKidsMode) "Unlock" else "Activate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
