package com.example.resenha.ui.group

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.resenha.data.ConversationParticipant
import com.example.resenha.data.UserProfile
import com.example.resenha.network.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch
import android.util.Log
import androidx.compose.foundation.layout.width

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupManagementScreen(
    conversationId: String,
    currentName: String,
    currentImageUrl: String?,
    onNameUpdated: (String) -> Unit,
    onImageChanged: (String?) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val blueColor = Color(0xFF94ADFF)
    var newName by remember { mutableStateOf(currentName) }
    var newImageUrl by remember { mutableStateOf(currentImageUrl ?: "") }
    var searchUser by remember { mutableStateOf("") }
    var availableUsers by remember { mutableStateOf(listOf<UserProfile>()) }
    var isSaving by remember { mutableStateOf(false) }
    val currentUserId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: ""

    // 👈 ESTADO LOCAL dos membros - resolve tudo!
    var localMembers by remember { mutableStateOf(listOf<UserProfile>()) }

    // 👈 CARREGA MEMBROS na inicialização
    LaunchedEffect(conversationId) {
        scope.launch {
            try {
                val participants = SupabaseClient.client.from("conversation_participants")
                    .select { filter { eq("conversation_id", conversationId) } }
                    .decodeList<ConversationParticipant>()

                localMembers = participants.mapNotNull { participant ->
                    try {
                        SupabaseClient.client.from("users")
                            .select { filter { eq("id", participant.user_id) } }
                            .decodeSingle<UserProfile>()
                    } catch (e: Exception) { null }
                }
                Log.d("GROUP_MEMBERS", "Carregados ${localMembers.size} membros")
            } catch (e: Exception) {
                Log.e("GROUP_MEMBERS", "Erro carregar membros", e)
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val bytes = context.contentResolver.openInputStream(it)?.use { input -> input.readBytes() }
                    if (bytes != null) {
                        val fileName = "group_${conversationId}_${System.currentTimeMillis()}.jpg"
                        SupabaseClient.client.storage.from("resenha").upload(fileName, bytes)
                        val publicUrl = SupabaseClient.client.storage.from("resenha").publicUrl(fileName)
                        newImageUrl = publicUrl
                    }
                } catch (e: Exception) {
                    Log.e("GROUP_MGMT", "Erro foto", e)
                }
            }
        }
    }

    // Busca usuários disponíveis
    LaunchedEffect(searchUser) {
        if (searchUser.length > 1) {
            scope.launch {
                try {
                    availableUsers = SupabaseClient.client.from("users")
                        .select {
                            filter {
                                ilike("name", "%$searchUser%")
                                neq("id", currentUserId)
                            }
                        }.decodeList<UserProfile>()
                        .filter { user -> localMembers.none { it.id == user.id } }  // 👈 localMembers!
                } catch (e: Exception) {
                    Log.e("GROUP_SEARCH", "Erro busca", e)
                }
            }
        } else {
            availableUsers = emptyList()
        }
    }

    // ADICIONAR MEMBRO
    val addMember = { userId: String ->
        scope.launch {
            try {
                SupabaseClient.client.from("conversation_participants").upsert(
                    ConversationParticipant(
                        conversation_id = conversationId,
                        user_id = userId
                    )
                ) { ignoreDuplicates = true }

                // 👈 RECARREGA LOCALMENTE
                val participants = SupabaseClient.client.from("conversation_participants")
                    .select { filter { eq("conversation_id", conversationId) } }
                    .decodeList<ConversationParticipant>()

                localMembers = participants.mapNotNull { participant ->
                    try {
                        SupabaseClient.client.from("users")
                            .select { filter { eq("id", participant.user_id) } }
                            .decodeSingle<UserProfile>()
                    } catch (e: Exception) { null }
                }

                Log.d("GROUP_ADD", "Adicionado: $userId. Total: ${localMembers.size}")
            } catch (e: Exception) {
                Log.e("GROUP_ADD", "Erro adicionar", e)
            }
        }
    }

    // REMOVER MEMBRO
    val removeMember = { userId: String ->
        scope.launch {
            try {
                SupabaseClient.client.from("conversation_participants").delete {
                    filter {
                        eq("conversation_id", conversationId)
                        eq("user_id", userId)
                    }
                }

                // 👈 RECARREGA LOCALMENTE (igual add)
                val participants = SupabaseClient.client.from("conversation_participants")
                    .select { filter { eq("conversation_id", conversationId) } }
                    .decodeList<ConversationParticipant>()

                localMembers = participants.mapNotNull { participant ->
                    try {
                        SupabaseClient.client.from("users")
                            .select { filter { eq("id", participant.user_id) } }
                            .decodeSingle<UserProfile>()
                    } catch (e: Exception) { null }
                }

                Log.d("GROUP_REMOVE", "Removido: $userId. Total: ${localMembers.size}")
            } catch (e: Exception) {
                Log.e("GROUP_REMOVE", "Erro remover", e)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gerenciar Grupo", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Fechar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding( 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Botão Voltar ao Grupo
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text(
                        "Voltar ao Grupo",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Botão Salvar (último!)
                Button(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            try {
                                SupabaseClient.client.from("conversations").update({
                                    set("name", newName)
                                    set("group_image_url", newImageUrl.ifBlank { null })
                                }) { filter { eq("id", conversationId) } }
                                onNameUpdated(newName)
                                onImageChanged(newImageUrl.ifBlank { null })
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = blueColor),
                    enabled = newName.isNotBlank()
                ) {
                    Text(
                        "Salvar Alterações",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        },
        containerColor = Color(0xFFF5F7FF)
    ) { padding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                // Foto
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.BottomEnd) {
                        AsyncImage(
                            model = newImageUrl.ifBlank { "https://ui-avatars.com/api/?name=${newName.take(1)}&background=94ADFF&color=fff" },
                            contentDescription = "Foto do grupo",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color.LightGray),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(blueColor)
                                .clickable { imagePickerLauncher.launch("image/*") }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Foto", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                // Nome
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nome do grupo") },
                    leadingIcon = { Icon(Icons.Default.Group, null, tint = blueColor) },
                    textStyle = LocalTextStyle.current.copy(color = blueColor),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 👈 MEMBROS ATUAIS (localMembers)
                Text("Membros (${localMembers.size})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                LazyRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(localMembers) { member ->
                        Card(
                            modifier = Modifier.size(70.dp)
                        ) {
                            Box {
                                AsyncImage(
                                    model = member.profile_image_url ?: "https://ui-avatars.com/api/?name=${member.name?.take(1)}",
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { removeMember(member.id) },
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                ) {
                                    Icon(Icons.Default.Delete, "Remover", tint = Color.Red)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 👈 BUSCA USUÁRIOS
                Text("Adicionar membros", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                OutlinedTextField(
                    value = searchUser,
                    onValueChange = { searchUser = it },
                    label = { Text("Buscar usuário", color = blueColor) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = blueColor) },
                    textStyle = LocalTextStyle.current.copy(color = blueColor),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableUsers) { user ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { addMember(user.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = user.profile_image_url ?: "https://ui-avatars.com/api/?name=${user.name?.take(1)}",
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(user.name ?: "", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }








            }
        if (isSaving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f)),  // Fundo escuro
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = blueColor)
            }
        }
        }
    }

