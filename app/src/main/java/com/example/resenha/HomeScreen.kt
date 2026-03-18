package com.example.resenha

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.resenha.data.Conversation
import com.example.resenha.data.ConversationParticipant
import com.example.resenha.data.UserProfile
import com.example.resenha.network.SupabaseClient
import com.example.resenha.ui.user.ProfileViewModel
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
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
    val contactImageUrl: String?,
    val unreadCount: Int
)

@OptIn(ExperimentalMaterial3Api::class, InternalSerializationApi::class)
@Composable
fun HomeScreen(
    onConversationClick: (Conversation) -> Unit,
    onNewChatClick: () -> Unit,
    onNewGroupClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val scope = rememberCoroutineScope()
    val blueColor = Color(0xFF94ADFF)
    val whatsappGreen = Color(0xFF25D366)

    var conversationsList by remember { mutableStateOf(listOf<ChatItemUiState>()) }
    var isLoading by remember { mutableStateOf(true) }
    val currentUserId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: ""
    val context = androidx.compose.ui.platform.LocalContext.current

    var previousUnreadCount by remember { mutableStateOf(0) }
    var isInitialLoad by remember { mutableStateOf(true) }

    // --- VARIÁVEIS DE PESQUISA NA TELA INICIAL ---
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> }
    )

    val profileViewModel: ProfileViewModel = viewModel()
    var profileImageUrl by remember { mutableStateOf<String?>(null) }
    var userName by remember { mutableStateOf("") }

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
                val profiles = SupabaseClient.client.from("users").select().decodeList<UserProfile>()
                val usersMap = profiles.associateBy { it.id }

                val myParticipations = SupabaseClient.client.from("conversation_participants")
                    .select { filter { eq("user_id", currentUserId) } }
                    .decodeList<ConversationParticipant>()
                val myGroupIds = myParticipations.map { it.conversation_id }

                val privateConvs = SupabaseClient.client.from("conversations")
                    .select { filter {
                        or {
                            eq("user_1", currentUserId)
                            eq("user_2", currentUserId)
                        }
                    }}.decodeList<Conversation>()

                val groupConvs = if (myGroupIds.isNotEmpty()) {
                    SupabaseClient.client.from("conversations")
                        .select { filter { isIn("id", myGroupIds) } }
                        .decodeList<Conversation>()
                } else emptyList()

                val myConversations = (privateConvs + groupConvs).distinctBy { it.id }

                val unreadMsgs = SupabaseClient.client.from("messages")
                    .select {
                        filter {
                            neq("sender_id", currentUserId)
                            neq("status", "lida")
                        }
                    }.decodeList<MessageBadge>()

                val unreadMap = unreadMsgs.groupBy { it.conversation_id }.mapValues { it.value.size }

                val totalUnreadNow = unreadMsgs.size

                if (!isInitialLoad) {
                    if (totalUnreadNow > previousUnreadCount) {
                        val ultimaMsg = unreadMsgs.lastOrNull()
                        val nomeRemetente =
                            if (ultimaMsg != null) usersMap[ultimaMsg.sender_id]?.name
                                ?: "Novo usuário" else "Resenha"

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

                val mappedList = myConversations.map { conv ->
                    val displayContactName: String
                    val displayImageUrl: String?

                    if (conv.is_group) {
                        displayContactName = conv.name ?: "Grupo Sem Nome"
                        displayImageUrl = conv.group_image_url
                    } else {
                        val u1 = conv.user_1 ?: ""
                        val u2 = conv.user_2 ?: ""
                        val otherUserId = if (u1 == currentUserId) u2 else u1
                        displayContactName = usersMap[otherUserId]?.name ?: "Usuário"
                        displayImageUrl = usersMap[otherUserId]?.profile_image_url
                    }

                    val count = unreadMap[conv.id] ?: 0
                    ChatItemUiState(
                        conversation = conv,
                        contactName = displayContactName,
                        contactImageUrl = displayImageUrl,
                        unreadCount = count)
                }

                conversationsList = mappedList.sortedWith(
                    compareByDescending<ChatItemUiState> { it.conversation.is_pinned }
                        .thenByDescending { it.unreadCount > 0 }
                        .thenByDescending { it.conversation.last_message_time }
                )

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
                            } catch (e: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RESENHA_HOME", "Erro ao carregar conversas: ${e.message}", e)
            } finally {
                isLoading = false
            }
        }
    }


    fun togglePin(item: ChatItemUiState) {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        scope.launch {
            try {
                SupabaseClient.client.from("conversations").update(
                    {
                        set("is_pinned", !item.conversation.is_pinned)
                    }
                ) {
                    filter { eq("id", item.conversation.id) }
                }
                loadConversations()
            } catch (e: Exception) {
                println("Erro ao fixar: ${e.message}")
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

    LaunchedEffect(Unit) {
        val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
        if (userId != null) {
            try {
                val user = SupabaseClient.client.from("users")
                    .select { filter { eq("id", userId) } }
                    .decodeSingle<UserProfile>()

                profileImageUrl = user.profile_image_url
                userName = user.name

                android.util.Log.d("RESENHA_DEBUG", "URL carregada na Home: ${user.profile_image_url}")
            } catch (e: Exception) {
                android.util.Log.e("RESENHA_DEBUG", "Erro ao buscar usuário na Home", e)
            }
        }
    }

    var showFabMenu by remember { mutableStateOf(false)}

    // --- LÓGICA DE FILTRAGEM LOCAL ---
    val displayedConversations = remember(conversationsList, searchQuery) {
        if (searchQuery.isBlank()) {
            conversationsList
        } else {
            conversationsList.filter {
                it.contactName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar resenha...", color = Color.Gray) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.ArrowBack, null, tint = Color.Black)
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, null, tint = Color.Black)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = onProfileClick,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            val fallbackName = if (userName.isNotBlank()) userName else "User"
                            val finalUrl = profileImageUrl ?: "https://ui-avatars.com/api/?name=$fallbackName&background=94ADFF&color=fff"

                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(finalUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Meu Perfil",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.LightGray),
                                contentScale = ContentScale.Crop
                            )
                        }
                    },
                    title = { Text("Resenhas Ativas", fontWeight = FontWeight.Bold, color = Color.Black) },
                    actions = {
                        // BOTAO DE LUPA PARA ATIVAR A PESQUISA
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Buscar Resenha",
                                tint = Color.Black
                            )
                        }
                        IconButton(onClick = onLogoutClick) {
                            Icon(
                                Icons.Default.ExitToApp,
                                contentDescription = "Sair",
                                tint = Color.Black
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showFabMenu = true },
                    containerColor = blueColor,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nova Conversa")
                }

                DropdownMenu(
                    expanded = showFabMenu,
                    onDismissRequest = { showFabMenu = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    DropdownMenuItem(
                        text = { Text("Novo Chat Privado", color = blueColor) },
                        leadingIcon = {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = blueColor)
                        },
                        onClick = {
                            showFabMenu = false
                            onNewChatClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Novo Grupo", color = blueColor) },
                        leadingIcon = {
                            Icon(Icons.Filled.Group, contentDescription = null, tint = blueColor)
                        },
                        onClick = {
                            showFabMenu = false
                            onNewGroupClick()
                        }
                    )
                }
            }
        },
        containerColor = Color(0xFFF5F7FF)
    ) { padding ->
        if (isLoading && conversationsList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = blueColor)
            }
        } else if (conversationsList.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Forum, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Nenhuma resenha ainda.", color = Color.Black, fontWeight = FontWeight.Medium)
                Text("Toque no + para buscar pessoas!", color = Color.DarkGray)
            }
        } else if (displayedConversations.isEmpty() && isSearchActive) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Search, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Nenhuma resenha encontrada.", color = Color.Gray, fontWeight = FontWeight.Medium)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                // AQUI USAMOS A LISTA FILTRADA!
                items(displayedConversations, key = { it.conversation.id }) { item ->
                    ConversationItem(
                        item = item,
                        currentUserId = currentUserId,
                        blueColor = blueColor,
                        badgeColor = whatsappGreen,
                        onClick = { onConversationClick(item.conversation) },
                        onLongClick = { togglePin(item) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    item: ChatItemUiState,
    currentUserId: String,
    blueColor: Color,
    badgeColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {

    fun formatTimeDisplay(rawTime: String?): String {
        if (rawTime.isNullOrEmpty()) return ""
        if (rawTime.length <= 5) return rawTime

        return try {
            val parser =
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {

            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(item.contactImageUrl ?: "https://ui-avatars.com/api/?name=${item.contactName}&background=94ADFF&color=fff")
                    .crossfade(true)
                    .build(),
                contentDescription = "Foto do contato",
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color.LightGray),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.contactName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.Black
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.conversation.last_message_sender_id == currentUserId) {
                        val icon =
                            if (item.conversation.last_message_status == "enviada") Icons.Default.Check else Icons.Default.DoneAll
                        val iconTint =
                            if (item.conversation.last_message_status == "lida") Color(0xFF4CAF50) else Color(
                                0xFF9E9E9E
                            )
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = iconTint
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (item.conversation.is_pinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(45f),
                            tint = blueColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    val previewMessage = if (item.conversation.last_message.isNullOrEmpty()) {
                        "Inicie a conversa"
                    } else {
                        CryptoUtils.decrypt(item.conversation.last_message)
                    }

                    Text(previewMessage, maxLines = 1, fontSize = 14.sp, color = Color.DarkGray)
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
                        modifier = Modifier
                            .size(24.dp)
                            .background(badgeColor, CircleShape),
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