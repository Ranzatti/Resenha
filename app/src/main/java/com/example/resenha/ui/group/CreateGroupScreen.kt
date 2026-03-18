package com.example.resenha.ui.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.resenha.data.Conversation
import com.example.resenha.data.ConversationParticipant
import com.example.resenha.data.UserProfile
import com.example.resenha.network.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.draw.clip

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(onBack: () -> Unit, onGroupCreated: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var groupName by remember { mutableStateOf("") }
    var selectedUserIds by remember { mutableStateOf(setOf<String>()) }
    var usersList by remember { mutableStateOf(listOf<UserProfile>()) }
    val currentUserId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return
    val blueColor = Color(0xFF94ADFF)



    LaunchedEffect(Unit) {
        try {
            usersList = SupabaseClient.client.from("users")
                .select { filter { neq("id", currentUserId) } }
                .decodeList<UserProfile>()
        } catch (e: Exception) {
            android.util.Log.e("RESENHA_GRUPO", "Erro ao carregar usuários: ${e.message}")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Novo Grupo", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.LightGray)
            )
        },
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            android.util.Log.d("RESENHA_GRUPO", "Iniciando criação do grupo...")

                            val groupId = java.util.UUID.randomUUID().toString()
                            android.util.Log.d("RESENHA_GRUPO", "GroupId gerado: $groupId")

                            SupabaseClient.client.from("conversations").insert(  // ✅ Agora pode usar suspend
                                Conversation(id = groupId, isGroup = true, name = groupName)

                            )
                            android.util.Log.d("RESENHA_GRUPO", "Conversa inserida")

                            val participantsToInsert = selectedUserIds.map {
                                ConversationParticipant(conversation_id = groupId, user_id = it)
                            } + listOf(
                                ConversationParticipant(conversation_id = groupId, user_id = currentUserId, role = "admin")
                            )

                            SupabaseClient.client.from("conversation_participants").insert(participantsToInsert)
                            android.util.Log.d("RESENHA_GRUPO", "Participantes inseridos")

                            kotlinx.coroutines.delay(1000)  // ✅ Reduzido para 1s
                            android.util.Log.d("RESENHA_GRUPO", "Chamando onGroupCreated($groupId)")

                            onGroupCreated(groupId)
                        }
                    },
                    enabled = groupName.isNotBlank() && selectedUserIds.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Criar Grupo")
                }
            }
        },
        containerColor = Color(0xFFF5F7FF)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Nome do Grupo") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = blueColor,
                    focusedLabelColor = blueColor,
                    focusedTextColor = blueColor,
                    unfocusedTextColor = blueColor
                )
            )

            Text("Participantes:", Modifier.padding(vertical = 16.dp), fontWeight = FontWeight.Bold)

            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(usersList) { user ->
                    val isSelected = selectedUserIds.contains(user.id)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelected) selectedUserIds -= user.id
                                else selectedUserIds += user.id
                            }
                            .padding(12.dp)
                            .background(
                                if (isSelected) blueColor.copy(alpha = 0.1f) else Color.White,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Foto
                        AsyncImage(
                            model = user.profile_image_url ?: "https://ui-avatars.com/api/?name=${user.name?.take(1)}&background=94ADFF&color=fff",
                            contentDescription = null,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .border(
                                    if (isSelected) 2.dp else 0.dp,
                                    blueColor,
                                    CircleShape
                                ),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(Modifier.width(12.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                user.name ?: "Usuário",
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp
                            )
                            Text(
                                "Adicionar ao grupo",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }

                        AnimatedVisibility(isSelected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = blueColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }


        }
    }
}
