package com.htn.fishcare.chatbot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun RagChatbotRoute(
    modifier: Modifier = Modifier,
    viewModel: RagChatbotViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearError()
        }
    }

    RagChatbotScreen(
        modifier = modifier,
        uiState = uiState,
        onSendMessage = viewModel::sendMessage,
        onUserInputChange = viewModel::updateUserInput,
        onBackClick = onBackClick
    )
}

@Composable
fun RagChatbotScreen(
    modifier: Modifier = Modifier,
    uiState: ChatbotUiState,
    onSendMessage: (String) -> Unit,
    onUserInputChange: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI Chẩn đoán bệnh cá",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }

        // Messages list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.messages) { message ->
                ChatMessageBubble(message = message)
            }

            // Loading indicator
            if (uiState.isWaitingForResponse) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }

        // Error message
        if (uiState.error != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFCDD2)
                )
            ) {
                Text(
                    text = uiState.error,
                    color = Color.Red,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp
                )
            }
        }

        // Input area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.userInput,
                onValueChange = onUserInputChange,
                label = { Text("Mô tả triệu chứng của cá...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(8.dp)),
                enabled = !uiState.isLoading,
                minLines = 3,
                maxLines = 5
            )

            Button(
                onClick = { onSendMessage(uiState.userInput) },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp),
                enabled = !uiState.isLoading && uiState.userInput.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Send",
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Chẩn đoán")
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatbotMessage,
    modifier: Modifier = Modifier
) {
    val isUserMessage = message.role == "user"
    val backgroundColor = if (isUserMessage) {
        Color(0xFF1976D2)  // Blue for user
    } else {
        Color(0xFFC8E6C9)  // Green for assistant
    }

    val textColor = if (isUserMessage) {
        Color.White
    } else {
        Color.Black
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUserMessage) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f),
            colors = CardDefaults.cardColors(
                containerColor = backgroundColor
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = textColor,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}
