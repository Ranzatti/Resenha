package com.example.resenha.data

import android.util.Log
import android.util.Patterns
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.resenha.network.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    val userName = mutableStateOf("")
    val email = mutableStateOf("")
    val password = mutableStateOf("")
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)

    fun clearFields() {
        userName.value = ""
        email.value = ""
        password.value = ""
        errorMessage.value = null
        isLoading.value = false
    }

    private fun isEmailValid(email: String): Boolean =
        Patterns.EMAIL_ADDRESS.matcher(email).matches()

    fun signIn(onSuccess: () -> Unit) {
        val mail = email.value.trim()
        val pass = password.value.trim()

        if (mail.isBlank() || pass.isBlank()) {
            errorMessage.value = "Preencha todos os campos!"
            return
        }

        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                // Tentativa de login
                SupabaseClient.client.auth.signInWith(Email) {
                    email = mail
                    password = pass
                }
                onSuccess()
            } catch (e: Throwable) {
                // Throwable captura TUDO, inclusive erros de rede do Ktor 3.x
                Log.e("RESENHA_DEBUG", "Falha no Login", e)

                val errorMsg = e.message ?: ""
                errorMessage.value = when {
                    errorMsg.contains("invalid", ignoreCase = true) -> "E-mail ou senha incorretos."
                    errorMsg.contains("network", ignoreCase = true) -> "Sem conexão com a internet."
                    else -> "Usuário não encontrado ou dados inválidos."
                }
            } finally {
                isLoading.value = false
            }
        }
    }

    fun resetPassword() {
        val mail = email.value.trim()
        if (mail.isBlank()) {
            errorMessage.value = "Digite seu e-mail no campo acima primeiro."
            return
        }

        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                // O Supabase envia um link de redefinição para o e-mail
                SupabaseClient.client.auth.resetPasswordForEmail(mail)
                errorMessage.value = "Link de recuperação enviado para: $mail"
            } catch (e: Exception) {
                errorMessage.value = "Erro ao enviar: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun signUp(onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                // 1. Cria o usuário no Authentication
                SupabaseClient.client.auth.signUpWith(Email) {
                    email = this@AuthViewModel.email.value
                    password = this@AuthViewModel.password.value
                }

                val userId = SupabaseClient.client.auth.currentUserOrNull()?.id

                // 2. Grava na tabela 'users' apenas o que existe no seu banco
                if (userId != null) {
                    SupabaseClient.client.from("users").insert(
                        mapOf(
                            "id" to userId,
                            "name" to userName.value
                        )
                    )
                    onSuccess()
                }
            } catch (e: Exception) {
                errorMessage.value = "Erro: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }
}