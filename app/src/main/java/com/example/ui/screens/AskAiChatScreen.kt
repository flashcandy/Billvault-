package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.NothingButton
import com.example.ui.components.NothingHeader
import com.example.ui.components.NothingTextField
import com.example.ui.theme.DotoFontFamily
import com.example.ui.theme.NothingRed
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.util.UUID

enum class ChatSender { USER, AI }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: ChatSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun AskAiChatScreen(
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Session chat history (retained during session)
    val sessionMessages = remember {
        mutableStateListOf(
            ChatMessage(
                sender = ChatSender.AI,
                text = "Hello! I am your Nothing OS Bill Vault AI assistant. Type product or warranty details, or ask me anything about your bills."
            )
        )
    }

    var inputText by remember { mutableStateOf("") }

    fun sendMessage() {
        val trimmed = inputText.trim()
        if (trimmed.isBlank()) return

        sessionMessages.add(ChatMessage(sender = ChatSender.USER, text = trimmed))
        inputText = ""

        coroutineScope.launch {
            listState.animateScrollToItem(sessionMessages.size - 1)
        }

        // Placeholder response for Phase 1 session chat
        sessionMessages.add(
            ChatMessage(
                sender = ChatSender.AI,
                text = "I received: \"$trimmed\". Ask AI assistant session ready."
            )
        )

        coroutineScope.launch {
            listState.animateScrollToItem(sessionMessages.size - 1)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            NothingHeader(
                title = "ASK AI",
                subtitle = "Session chat assistant • Nothing OS Vault",
                action = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("ask_ai_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Ask AI",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            )

            // Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("chat_message_list"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sessionMessages, key = { it.id }) { msg ->
                    ChatMessageBubble(message = msg)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NothingTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = "YOUR MESSAGE",
                    placeholder = "Ask something or describe a bill...",
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    modifier = Modifier.weight(1f),
                    testTag = "chat_input_text"
                )

                NothingButton(
                    text = "SEND",
                    icon = Icons.AutoMirrored.Filled.Send,
                    isPrimary = true,
                    enabled = inputText.isNotBlank(),
                    onClick = { sendMessage() },
                    contentDescription = "Send message to AI assistant",
                    modifier = Modifier.width(100.dp),
                    testTag = "chat_send_button"
                )
            }
        }
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessage) {
    val isUser = message.sender == ChatSender.USER

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isUser) NothingRed.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surface
                )
                .border(
                    width = 1.dp,
                    color = if (isUser) NothingRed else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isUser) NothingRed else Color.White)
                )
                Text(
                    text = if (isUser) "YOU" else "ASK AI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = DotoFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = if (isUser) NothingRed else MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = SpaceGroteskFontFamily
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
