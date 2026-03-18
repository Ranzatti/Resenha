package com.example.resenha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.resenha.data.AuthViewModel
import com.example.resenha.data.Conversation
import com.example.resenha.network.SupabaseClient
import com.example.resenha.ui.auth.LoginScreen
import com.example.resenha.ui.auth.SignUpScreen
import com.example.resenha.ui.group.CreateGroupScreen
import com.example.resenha.ui.theme.ResenhaTheme
import com.example.resenha.ui.user.ProfileScreen
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val authViewModel: AuthViewModel = viewModel()
            val scope = rememberCoroutineScope()
            var currentScreen by remember { mutableStateOf("login") }
            var activeConversationId by remember { mutableStateOf("") }

            LaunchedEffect(Unit) {
                if (SupabaseClient.client.auth.currentUserOrNull() != null) {
                    currentScreen = "home"
                }
            }

            ResenhaTheme {
                when (currentScreen) {
                    "login" -> LoginScreen(
                        onLoginSuccess = { currentScreen = "home" },
                        onNavigateToSignUp = {
                            authViewModel.clearFields()
                            currentScreen = "signup"
                        }
                    )
                    "signup" -> SignUpScreen(
                        onBack = {
                            authViewModel.clearFields()
                            currentScreen = "login"
                        },
                        onSignUpSuccess = { currentScreen = "home" }
                    )
                    "home" -> HomeScreen(
                        onConversationClick = { chat ->
                            activeConversationId = chat.id
                            currentScreen = "chat"
                        },
                        onNewChatClick = { currentScreen = "search" },
                        onNewGroupClick = { currentScreen = "create_group" },
                        onLogoutClick = {
                            scope.launch {
                                try {
                                    SupabaseClient.client.auth.signOut()
                                    authViewModel.clearFields()
                                    currentScreen = "login"
                                } catch (e: Exception) {
                                    currentScreen = "login"
                                }
                            }
                        },
                        onProfileClick = { currentScreen = "profile"}

                    )
                    "profile" -> ProfileScreen(
                        onBack = { currentScreen = "home" }
                    )
                    "create_group" -> CreateGroupScreen(
                        onBack = { currentScreen = "home" },
                        onGroupCreated = { groupId ->
                            activeConversationId = groupId
                            currentScreen = "chat" // Ou volta pra 'home', como preferir
                        }
                    )

                    "search" -> SearchUsersScreen(
                        onBack = { currentScreen = "home" },
                        onUserSelected = { selectedUser ->
                            scope.launch {
                                try {
                                    val myId = SupabaseClient.client.auth.currentUserOrNull()?.id
                                    if (myId != null) {

                                        // 1. Verifica se já existe uma conversa entre os dois usuários
                                        val existing = SupabaseClient.client.from("conversations")
                                            .select {
                                                filter {
                                                    or {
                                                        and {
                                                            eq("user_1", myId)
                                                            eq("user_2", selectedUser.id)
                                                        }
                                                        and {
                                                            eq("user_1", selectedUser.id)
                                                            eq("user_2", myId)
                                                        }
                                                    }
                                                }
                                            }
                                            .decodeList<Conversation>()
                                            .firstOrNull()

                                        if (existing != null) {
                                            // 2. Já existe — abre direto
                                            activeConversationId = existing.id
                                            currentScreen = "chat"
                                        } else {
                                            // 3. Não existe — cria uma nova e navega
                                            val chatId = java.util.UUID.randomUUID().toString()
                                            SupabaseClient.client.from("conversations").insert(
                                                Conversation(
                                                    id = chatId,
                                                    user_1 = myId,
                                                    user_2 = selectedUser.id,
                                                    is_group = false,
                                                    last_message = "Iniciou uma resenha",
                                                    is_pinned = false,
                                                )
                                            )
                                            activeConversationId = chatId
                                            currentScreen = "chat"
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("RESENHA", "Erro ao criar chat: ${e.message}")
                                    currentScreen = "home"
                                }

                            }
                        }
                    )
                    "chat" -> ChatScreen(
                        conversationId = activeConversationId,
                        onBack = { currentScreen = "home" }
                    )
                }
            }
        }
    }
}