package com.example.resenha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.resenha.data.AuthViewModel
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
                                        SupabaseClient.client.from("conversations").insert(
                                            mapOf(
                                                "user_1" to myId,
                                                "user_2" to selectedUser.id,
                                                "last_message" to "Iniciou uma resenha"
                                            )
                                        )
                                        currentScreen = "home"
                                    }
                                } catch (e: Exception) {
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