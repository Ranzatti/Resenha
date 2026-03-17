package com.example.resenha.ui.user

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.resenha.data.UserProfile
import com.example.resenha.network.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch
import java.util.UUID

class ProfileViewModel : ViewModel() {
    val name = mutableStateOf("")
    val password = mutableStateOf("")
    val profileImageUrl = mutableStateOf<String?>(null)

    val isLoading = mutableStateOf(true)
    val isSaving = mutableStateOf(false)
    val uiMessage = mutableStateOf<String?>(null) // Para exibir Toasts/Snackbars na UI

    private val currentUserId: String?
        get() = SupabaseClient.client.auth.currentUserOrNull()?.id

    init {
        loadProfile()
    }

    fun loadProfile() {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            isLoading.value = true
            try {
                val user = SupabaseClient.client.from("users")
                    .select { filter { eq("id", userId) } }
                    .decodeSingle<UserProfile>()

                name.value = user.name
                profileImageUrl.value = user.profile_image_url
            } catch (e: Exception) {
                uiMessage.value = "Erro ao carregar perfil: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun uploadImage(bytes: ByteArray) {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            isSaving.value = true
            try {
                val fileName = "profiles/${userId}_${UUID.randomUUID()}.jpg"
                val bucket = SupabaseClient.client.storage.from("resenha")

                // 1. Faz o upload
                bucket.upload(fileName, bytes) {
                    upsert = true
                }

                // 2. Pega a URL
                val publicUrl = bucket.publicUrl(fileName)
                android.util.Log.d("RESENHA_DEBUG", "URL gerada no upload: $publicUrl")

                // 3. ATUALIZA O BANCO E VERIFICA SE DEU ERRO
                // É essencial passar a string correta do nome da coluna "profile_image_url"
                SupabaseClient.client.from("users").update(
                    { set("profile_image_url", publicUrl) }
                ) { filter { eq("id", userId) } }

                android.util.Log.d("RESENHA_DEBUG", "Banco atualizado com sucesso!")

                // Só atualiza a tela se não deu erro no banco
                profileImageUrl.value = publicUrl
                uiMessage.value = "Foto atualizada com sucesso!"
            } catch (e: Exception) {
                android.util.Log.e("RESENHA_DEBUG", "Erro FATAL no upload", e)
                uiMessage.value = "Erro ao subir imagem. Tente novamente."
            } finally {
                isSaving.value = false
            }
        }
    }




    fun saveProfile() {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            isSaving.value = true
            try {
                // Atualizar NOME e FOTO (se existir) juntos para evitar sobrescrever com null
                SupabaseClient.client.from("users").update(
                    {
                        set("name", name.value)
                        // Garante que o banco receba a URL atual, mantendo a foto que já estava
                        if (profileImageUrl.value != null) {
                            set("profile_image_url", profileImageUrl.value)
                        }
                    }
                ) { filter { eq("id", userId) } }

                // 2. Atualizar senha no Auth (se preenchida)
                if (password.value.isNotEmpty()) {
                    if (password.value.length < 6) {
                        uiMessage.value = "A senha deve ter pelo menos 6 caracteres"
                        isSaving.value = false
                        return@launch
                    } else {
                        SupabaseClient.client.auth.updateUser {
                            password = this@ProfileViewModel.password.value
                        }
                    }
                }

                uiMessage.value = "Perfil atualizado com sucesso!"
                password.value = ""
            } catch (e: Exception) {
                uiMessage.value = "Erro ao salvar: ${e.message}"
            } finally {
                isSaving.value = false
            }

        }
    }


    fun clearMessage() {
        uiMessage.value = null
    }
}

