package com.example.resenha

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resenha.data.Conversation
import com.example.resenha.network.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.InternalSerializationApi

@Serializable
data class MessageBadge(
    val id: String,
    val conversation_id: String,
    val sender_id: String,
    val content: String? = null,
    val created_at: String? = null,
    val status: String = "enviada"
)

data class ChatItemUiState(
    val conversation: Conversation,
    val contactName: String,
    val unreadCount: Int
)

@OptIn(ExperimentalMaterial3Api::class, InternalSerializationApi::class)
@Composable
fun HomeScreen(
    onConversationClick: (Conversation) -> Unit,
    onNewChatClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val blueColor = Color(0xFF94ADFF)
    val whatsappGreen = Color(0xFF25D366)

    var conversationsList by remember { mutableStateOf(listOf<ChatItemUiState>()) }
    var isLoading by remember { mutableStateOf(true) }
    val currentUserId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: ""
    val context = androidx.compose.ui.platform.LocalContext.current

    var previousUnreadCount by remember { mutableStateOf(0) }
    var isInitialLoad by remember { mutableStateOf(true) }

    val launcher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> }
    )

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun loadConversations() {
        scope.launch {
            try {
                val result = SupabaseClient.client.from("conversations").select().decodeList<Conversation>()
                val myConversations = result.filter { it.user_1 == currentUserId || it.user_2 == currentUserId }

                val profiles = SupabaseClient.client.from("users").select().decodeList<UserProfile>()
                val usersMap = profiles.associateBy { it.id }

                val unreadMsgs = SupabaseClient.client.from("messages")
                    .select {
                        filter {
                            neq("sender_id", currentUserId)
                            neq("status", "lida")
                        }
                    }.decodeList<MessageBadge>()

                val unreadMap = unreadMsgs.groupBy { it.conversation_id }.mapValues { it.value.size }

                // --- LÓGICA CORRIGIDA: DISPARO DA NOTIFICAÇÃO ---
                val totalUnreadNow = unreadMsgs.size

                if (!isInitialLoad) {
                    if (totalUnreadNow > previousUnreadCount) {
                        val ultimaMsg = unreadMsgs.lastOrNull()
                        val nomeRemetente = if (ultimaMsg != null) usersMap[ultimaMsg.sender_id]?.name ?: "Novo usuário" else "Resenha"

                        NotificationHelper.showNotification(
                            context = context,
                            title = "Mensagem de $nomeRemetente",
                            message = "Você tem novas mensagens!"
                        )
                    }
                } else {
                    isInitialLoad = false
                }

                previousUnreadCount = totalUnreadNow
                // ---------------------------------------------------

                val mappedList = myConversations.map { conv ->
                    val otherUserId = if (conv.user_1 == currentUserId) conv.user_2 else conv.user_1
                    val name = usersMap[otherUserId]?.name ?: "Usuário"
                    val count = unreadMap[conv.id] ?: 0
                    ChatItemUiState(conversation = conv, contactName = name, unreadCount = count)
                }

                conversationsList = mappedList.sortedByDescending { it.unreadCount > 0 }

                val convsToUpdate = mappedList.filter {
                    it.conversation.last_message_sender_id != currentUserId &&
                            it.conversation.last_message_status == "enviada"
                }

                if (convsToUpdate.isNotEmpty()) {
                    convsToUpdate.forEach { item ->
                        scope.launch {
                            try {
                                SupabaseClient.client.from("conversations").update(
                                    { set("last_message_status", "recebida") }
                                ) { filter { eq("id", item.conversation.id) } }

                                SupabaseClient.client.from("messages").update(
                                    { set("status", "recebida") }
                                ) {
                                    filter {
                                        eq("conversation_id", item.conversation.id)
                                        eq("status", "enviada")
                                    }
                                }
                            } catch(e: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadConversations()

        scope.launch {
            try {
                val channel = SupabaseClient.client.channel("home-updates")
                val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "conversations"
                }
                launch {
                    changeFlow.collect { loadConversations() }
                }
                channel.subscribe()
            } catch (e: Exception) {}
        }

        scope.launch {
            while (true) {
                delay(3000)
                loadConversations()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resenhas Ativas", fontWeight = FontWeight.Bold, color = Color.Black) },
                actions = {
                    IconButton(onClick = onLogoutClick) { Icon(Icons.Default.ExitToApp, null, tint = Color.Black) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewChatClick, containerColor = blueColor, contentColor = Color.White) {
                Icon(Icons.Default.Add, null)
            }
        },
        containerColor = Color(0xFFF5F7FF)
    ) { padding ->
        if (isLoading && conversationsList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = blueColor)
            }
        } else if (conversationsList.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Forum, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Nenhuma resenha ainda.", color = Color.Black, fontWeight = FontWeight.Medium)
                Text("Toque no + para buscar pessoas!", color = Color.DarkGray)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                items(conversationsList, key = { it.conversation.id }) { item ->
                    ConversationItem(item, currentUserId, blueColor, whatsappGreen) { onConversationClick(item.conversation) }
                }
            }
        }
    }
}

@Composable
fun ConversationItem(item: ChatItemUiState, currentUserId: String, blueColor: Color, badgeColor: Color, onClick: () -> Unit) {

    fun formatTimeDisplay(rawTime: String?): String {
        if (rawTime.isNullOrEmpty()) return ""
        if (rawTime.length <= 5) return rawTime

        return try {
            val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            parser.timeZone = java.util.TimeZone.getTimeZone("UTC")

            val cleanTime = rawTime.substringBefore(".").substringBefore("+").substringBefore("Z")
            val date = parser.parse(cleanTime)

            val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            formatter.timeZone = java.util.TimeZone.getDefault()
            formatter.format(date!!)
        } catch (e: Exception) {
            if (rawTime.contains("T")) rawTime.substringAfter("T").take(5) else rawTime.take(5)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(50.dp).background(blueColor, CircleShape), contentAlignment = Alignment.Center) {
                Text(item.contactName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(item.contactName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.conversation.last_message_sender_id == currentUserId) {
                        val icon = if (item.conversation.last_message_status == "enviada") Icons.Default.Check else Icons.Default.DoneAll
                        val iconTint = if (item.conversation.last_message_status == "lida") Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = iconTint)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(item.conversation.last_message ?: "Inicie a conversa", maxLines = 1, fontSize = 14.sp, color = Color.DarkGray)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (item.conversation.last_message_time != null) {
                    Text(
                        text = formatTimeDisplay(item.conversation.last_message_time),
                        color = if (item.unreadCount > 0) badgeColor else Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = if (item.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                    )
                }

                if (item.unreadCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier.size(24.dp).background(badgeColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.unreadCount.toString(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}