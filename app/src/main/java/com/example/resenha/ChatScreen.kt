package com.example.resenha

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Send
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(InternalSerializationApi::class)
@Serializable
data class ChatMessage(
    val id: String,
    val conversation_id: String,
    val sender_id: String,
    val content: String,
    val created_at: String,
    val status: String = "enviada"
)

@OptIn(ExperimentalMaterial3Api::class, InternalSerializationApi::class)
@Composable
fun ChatScreen(conversationId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val blueColor = Color(0xFF94ADFF)
    var messageText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var contactName by remember { mutableStateOf("Carregando...") }
    val currentUserId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: ""

    fun recarregarMensagens() {
        scope.launch {
            try {
                val msgs = SupabaseClient.client.from("messages")
                    .select { filter { eq("conversation_id", conversationId) } }.decodeList<ChatMessage>()
                messages = msgs

                val mensagensNaoLidas = msgs.filter { it.sender_id != currentUserId && it.status != "lida" }
                if (mensagensNaoLidas.isNotEmpty()) {
                    SupabaseClient.client.from("messages").update(
                        { set("status", "lida") }
                    ) {
                        filter {
                            eq("conversation_id", conversationId)
                            neq("sender_id", currentUserId)
                            neq("status", "lida")
                        }
                    }
                    SupabaseClient.client.from("conversations").update(
                        { set("last_message_status", "lida") }
                    ) { filter { eq("id", conversationId) } }
                }
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(conversationId) {
        recarregarMensagens()

        scope.launch {
            try {
                val convs = SupabaseClient.client.from("conversations")
                    .select { filter { eq("id", conversationId) } }.decodeList<Conversation>()
                val conv = convs.firstOrNull()

                if (conv != null) {
                    val otherId = if (conv.user_1 == currentUserId) conv.user_2 else conv.user_1
                    val otherUsers = SupabaseClient.client.from("users")
                        .select { filter { eq("id", otherId) } }.decodeList<UserProfile>()
                    contactName = otherUsers.firstOrNull()?.name ?: "Resenha"
                }
            } catch (e: Exception) { }
        }

        scope.launch {
            try {
                val channel = SupabaseClient.client.channel("chat-$conversationId")
                val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public")

                launch { changeFlow.collect { recarregarMensagens() } }
                channel.subscribe()
            } catch (e: Exception) { }
        }

        scope.launch {
            while (true) {
                delay(3000)
                recarregarMensagens()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contactName, fontWeight = FontWeight.Bold, color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.Black) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF5F7FF)
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp), reverseLayout = true) {
                items(messages.reversed(), key = { it.id }) { msg ->
                    val isMine = msg.sender_id == currentUserId
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier.background(
                                color = if (isMine) blueColor else Color.White,
                                shape = RoundedCornerShape(12.dp)
                            ).padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = msg.content,
                                    color = if (isMine) Color.White else Color.Black,
                                    fontSize = 16.sp
                                )
                                if (isMine) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val icon = if (msg.status == "enviada") Icons.Default.Check else Icons.Default.DoneAll
                                    val iconTint = if (msg.status == "lida") Color(0xFF4CAF50) else Color(0xFFE0E0E0)
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = iconTint
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Text(errorMessage!!, color = Color.Red, modifier = Modifier.padding(16.dp), fontSize = 14.sp)
            }

            Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Digite sua mensagem...", color = Color.Black) },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.Black, unfocusedTextColor = Color.Black, cursorColor = Color.Black, focusedBorderColor = blueColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (messageText.trim().isNotEmpty()) {
                            val textToSend = messageText
                            messageText = ""
                            errorMessage = null
                            scope.launch {
                                try {
                                    // GERA A DATA/HORA COMPLETA EM UTC PARA O SUPABASE NÃO RECLAMAR
                                    val timeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                                    timeFormat.timeZone = TimeZone.getTimeZone("UTC")
                                    val currentTime = timeFormat.format(Date())

                                    SupabaseClient.client.from("messages").insert(
                                        mapOf(
                                            "conversation_id" to conversationId,
                                            "sender_id" to currentUserId,
                                            "content" to textToSend,
                                            "status" to "enviada"
                                        )
                                    )
                                    SupabaseClient.client.from("conversations").update(
                                        {
                                            set("last_message", textToSend)
                                            set("last_message_sender_id", currentUserId)
                                            set("last_message_status", "enviada")
                                            set("last_message_time", currentTime)
                                        }
                                    ) { filter { eq("id", conversationId) } }

                                    recarregarMensagens()
                                } catch (e: Exception) {
                                    errorMessage = "Erro ao enviar: ${e.message}"
                                }
                            }
                        }
                    },
                    modifier = Modifier.background(blueColor, CircleShape)
                ) {
                    Icon(Icons.Default.Send, null, tint = Color.White)
                }
            }
        }
    }
}