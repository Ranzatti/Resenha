package com.example.resenha.ui.group

import android.net.Uri
import android.util.Log
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.style.TextOverflow
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

    var localMembers by remember { mutableStateOf(listOf<UserProfile>()) }

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
                        .filter { user -> localMembers.none { it.id == user.id } }
                } catch (e: Exception) {
                    Log.e("GROUP_SEARCH", "Erro busca", e)
                }
            }
        } else {
            availableUsers = emptyList()
        }
    }

    // --- ATUALIZAÇÃO OTIMISTA (ADICIONAR) ---
    val addMember = { user: UserProfile ->
        if (localMembers.none { it.id == user.id }) {
            // 1. Altera a Interface Instantaneamente
            localMembers = localMembers + user
            availableUsers = availableUsers.filter { it.id != user.id }
            searchUser = "" // Limpa o campo de busca

            // 2. Salva no banco de dados de forma invisível
            scope.launch {
                try {
                    SupabaseClient.client.from("conversation_participants").upsert(
                        ConversationParticipant(
                            conversation_id = conversationId,
                            user_id = user.id
                        )
                    ) { ignoreDuplicates = true }
                } catch (e: Exception) {
                    Log.e("GROUP_ADD", "Erro adicionar", e)
                    // Reverte a animação caso dê erro de internet
                    localMembers = localMembers.filter { it.id != user.id }
                }
            }
        }
    }

    // --- ATUALIZAÇÃO OTIMISTA (REMOVER) ---
    val removeMember = { user: UserProfile ->
        // 1. Remove da Interface Instantaneamente
        localMembers = localMembers.filter { it.id != user.id }

        // 2. Remove do banco de dados de forma invisível
        scope.launch {
            try {
                SupabaseClient.client.from("conversation_participants").delete {
                    filter {
                        eq("conversation_id", conversationId)
                        eq("user_id", user.id)
                    }
                }
            } catch (e: Exception) {
                Log.e("GROUP_REMOVE", "Erro remover", e)
                // Devolve o usuário para a tela em caso de erro
                localMembers = localMembers + user
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gerenciar Grupo", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Fechar", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9E9E9E))
                ) {
                    Text("Voltar", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

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
                                onBack()
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = blueColor),
                    enabled = newName.isNotBlank()
                ) {
                    Text("Salvar", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        },
        containerColor = Color.White
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Foto do Grupo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.BottomEnd) {
                    AsyncImage(
                        model = newImageUrl.ifBlank { "https://ui-avatars.com/api/?name=${newName.take(1)}&background=94ADFF&color=fff" },
                        contentDescription = "Foto do grupo",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFFF0F0F0)),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(blueColor)
                            .clickable { imagePickerLauncher.launch("image/*") }
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Foto", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Nome do Grupo
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Nome do grupo", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Group, null, tint = blueColor) },
                textStyle = LocalTextStyle.current.copy(color = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = blueColor,
                    focusedLabelColor = blueColor,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Membros Atuais
            Text(
                text = "Membros do Grupo (${localMembers.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Passa o objeto "member" inteiro para a função de remover
                items(localMembers) { member ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(64.dp)
                    ) {
                        Box(modifier = Modifier.size(56.dp)) {
                            AsyncImage(
                                model = member.profile_image_url ?: "https://ui-avatars.com/api/?name=${member.name?.take(1)}&background=94ADFF&color=fff",
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color(0xFFF0F0F0)),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(Color.Red)
                                    .border(1.5.dp, Color.White, CircleShape)
                                    .clickable { removeMember(member) }, // OTIMIZADO
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = member.name ?: "",
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Busca de Usuários
            Text(
                text = "Adicionar novos membros",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            OutlinedTextField(
                value = searchUser,
                onValueChange = { searchUser = it },
                placeholder = { Text("Procurar por nome...", color = Color.DarkGray) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = blueColor) },
                textStyle = LocalTextStyle.current.copy(color = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = blueColor,
                    unfocusedContainerColor = Color(0xFFF5F7FF),
                    focusedContainerColor = Color(0xFFF5F7FF),
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Passa o objeto "user" inteiro para a função de adicionar
                items(availableUsers) { user ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { addMember(user) }, // OTIMIZADO
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F7FF)),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = user.profile_image_url ?: "https://ui-avatars.com/api/?name=${user.name?.take(1)}&background=94ADFF&color=fff",
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.LightGray),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(user.name ?: "", fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        }

        if (isSaving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = blueColor)
            }
        }
    }
}