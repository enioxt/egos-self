package org.egos.self.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.egos.self.ui.theme.EgosSelfTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EgosSelfTheme {
                EgosSelfScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EgosSelfScreen(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showRelayDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("EGOS Self", fontWeight = FontWeight.Bold)
                        Text(
                            state.connectionStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Relay indicator
                    if (state.relayConnected) {
                        Badge(containerColor = Color(0xFF4CAF50)) {
                            Text("WAN", fontSize = 8.sp, color = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    // Ping button
                    TextButton(onClick = { viewModel.sendPing() }) {
                        Text("Ping")
                    }
                    // Relay button
                    TextButton(onClick = { showRelayDialog = true }) {
                        Text("Relay")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Devices section
            if (state.devices.isNotEmpty()) {
                DevicesBar(
                    devices = state.devices,
                    onDeviceClick = { viewModel.connectToDevice(it) }
                )
            }

            // Messages
            val listState = rememberLazyListState()
            LaunchedEffect(state.messages.size) {
                if (state.messages.isNotEmpty()) {
                    listState.animateScrollToItem(state.messages.size - 1)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "EGOS Self",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF60A5FA)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Your personal intelligence channel.\nMessages between you and your devices.",
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    "Waiting for devices on WiFi...",
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                items(state.messages) { msg ->
                    MessageBubble(msg)
                }
            }

            // Input bar
            MessageInput(
                value = state.messageInput,
                onValueChange = { viewModel.updateMessageInput(it) },
                onSend = { viewModel.sendMessage(state.messageInput) }
            )
        }
    }

    // Relay dialog
    if (showRelayDialog) {
        RelayDialog(
            onDismiss = { showRelayDialog = false },
            onConnect = { url ->
                viewModel.connectRelay(url)
                showRelayDialog = false
            }
        )
    }
}

@Composable
fun DevicesBar(devices: List<org.egos.self.protocol.DeviceInfo>, onDeviceClick: (org.egos.self.protocol.DeviceInfo) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E293B))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        devices.forEach { device ->
            AssistChip(
                onClick = { onDeviceClick(device) },
                label = { Text(device.deviceName, fontSize = 12.sp) },
                leadingIcon = {
                    Badge(
                        containerColor = if (device.reachable) Color(0xFF4CAF50) else Color(0xFF757575)
                    ) {}
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFF334155),
                    labelColor = Color.White
                )
            )
        }
    }
}

@Composable
fun MessageBubble(msg: MessageItem) {
    val isMe = msg.from == "me"
    val isSystem = msg.type == "system"

    if (isSystem) {
        Text(
            msg.text,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            textAlign = TextAlign.Center,
            color = Color(0xFF64748B),
            fontSize = 11.sp
        )
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isMe) 12.dp else 2.dp,
                bottomEnd = if (isMe) 2.dp else 12.dp
            ),
            color = if (isMe) Color(0xFF2563EB) else Color(0xFF1E293B),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!isMe) {
                    Text(msg.from, fontSize = 10.sp, color = Color(0xFF60A5FA), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                }
                Text(msg.text, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun MessageInput(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E293B))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message...", color = Color(0xFF64748B)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF2563EB),
                unfocusedBorderColor = Color(0xFF334155),
                cursorColor = Color(0xFF60A5FA)
            ),
            shape = RoundedCornerShape(24.dp),
            singleLine = true
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onSend,
            enabled = value.isNotBlank(),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2563EB),
                disabledContainerColor = Color(0xFF334155)
            )
        ) {
            Text("Send", color = Color.White)
        }
    }
}

@Composable
fun RelayDialog(onDismiss: () -> Unit, onConnect: (String) -> Unit) {
    var url by remember { mutableStateOf("ws://") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect to Relay", color = Color.White) },
        text = {
            Column {
                Text("Enter relay server URL:", color = Color(0xFF94A3B8))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("ws://your-vps:8765") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF2563EB)
                    )
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Start relay on your PC: egos relay",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConnect(url) }) { Text("Connect") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = Color(0xFF1E293B)
    )
}
