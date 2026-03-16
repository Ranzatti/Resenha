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
import com.example.resenha.ui.theme.ResenhaTheme
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
                        }
                    )
                    "search" -> SearchUsersScreen(
                        onBack = { currentScreen = "home" },
                        onUserSelected = { selectedUser ->
                            scope.launch {
                                try {
                                    val myId = SupabaseClient.client.auth.currentUserOrNull()?.id
                                    if (myId != null) {

                                        // 1. Verifica se já existe uma conversa com essa pessoa
                                        val existingConvs = SupabaseClient.client.from("conversations")
                                            .select()
                                            .decodeList<Conversation>()

                                        val existingChat = existingConvs.find {
                                            (it.user_1 == myId && it.user_2 == selectedUser.id) ||
                                                    (it.user_1 == selectedUser.id && it.user_2 == myId)
                                        }

                                        if (existingChat != null) {
                                            // Já existe! Abre a conversa direta
                                            activeConversationId = existingChat.id
                                            currentScreen = "chat"
                                        } else {
                                            // 2. Não existe. Cria uma nova e pega o ID retornado
                                            val newChats = SupabaseClient.client.from("conversations").insert(
                                                mapOf(
                                                    "user_1" to myId,
                                                    "user_2" to selectedUser.id,
                                                    "last_message" to CryptoUtils.encrypt("Iniciou uma resenha")
                                                )
                                            ) {
                                                select() // Exige que o Supabase devolva a linha recém-criada
                                            }.decodeList<Conversation>()

                                            // 3. Joga o usuário direto para a tela de Chat
                                            if (newChats.isNotEmpty()) {
                                                activeConversationId = newChats.first().id
                                                currentScreen = "chat"
                                            } else {
                                                currentScreen = "home"
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    currentScreen = "home" // Se algo falhar, volta para a home em segurança
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