/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository

/**
 * Private Profile Setup Screen (Pillar 4: Dual-Identity Architecture).
 * Enables setup of private friend identity completely isolated from public video channels.
 */
@Composable
fun PrivateProfileSetupScreen(
    repository: NexoraStreamRepository,
    viewModel: PrivateAuthViewModel = remember { PrivateAuthViewModel(repository) },
    onProfileCreated: () -> Unit
) {
    val handle by viewModel.handleInput.collectAsState()
    val displayName by viewModel.displayNameInput.collectAsState()
    val isTaken by viewModel.isHandleTaken.collectAsState()
    val isChecking by viewModel.isCheckingHandle.collectAsState()
    val isFormValid by viewModel.isFormValid.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .testTag("private_profile_setup_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Enclave Shield Icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = Color(0xFF3EA6FF),
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Private Friends Enclave",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Create your separate private identity. Private handles never appear in public video searches or channel indexes.",
                color = Color.Gray,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Display Name Input
            OutlinedTextField(
                value = displayName,
                onValueChange = { viewModel.onDisplayNameChanged(it) },
                label = { Text("Display Name") },
                placeholder = { Text("e.g. Alex") },
                leadingIcon = {
                    Icon(Icons.Outlined.Person, contentDescription = null, tint = Color.LightGray)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("private_display_name_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF3EA6FF),
                    unfocusedBorderColor = Color(0xFF333333)
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Handle Input with 300ms debounce validation
            OutlinedTextField(
                value = handle,
                onValueChange = { viewModel.onHandleChanged(it) },
                label = { Text("Private Handle (@handle)") },
                placeholder = { Text("@alex_private") },
                leadingIcon = {
                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = Color.LightGray)
                },
                trailingIcon = {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFF3EA6FF),
                            strokeWidth = 2.dp
                        )
                    } else if (handle.length >= 4 && !isTaken) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Available",
                            tint = Color(0xFF2BA640),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                isError = isTaken,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("private_handle_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = if (isTaken) Color(0xFFFF453A) else Color(0xFF3EA6FF),
                    unfocusedBorderColor = if (isTaken) Color(0xFFFF453A) else Color(0xFF333333),
                    errorBorderColor = Color(0xFFFF453A)
                ),
                singleLine = true
            )

            // Real-time Taken Feedback
            if (isTaken) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Handle already taken",
                    color = Color(0xFFFF453A),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Start)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Continue Button (disabled if invalid or taken)
            Button(
                onClick = {
                    viewModel.registerPrivateProfile {
                        onProfileCreated()
                    }
                },
                enabled = isFormValid,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFF222222),
                    disabledContentColor = Color.Gray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("activate_private_profile_button")
            ) {
                Text(
                    text = "Activate Private Enclave",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
