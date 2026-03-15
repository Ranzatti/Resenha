package com.example.resenha

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.resenha.data.Conversation
import com.example.resenha.network.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.InternalSerializationApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@OptIn(InternalSerializationApi::class)
@Serializable
data class ChatMessage(
    val id: String,
    val conversation_id: String,
    val sender_id: String,
    val content: String,
    val created_at: String,
    val status: String = "enviada",
    val media_url: String? = null,
    val media_type: String? = null
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

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var isUploading by remember { mutableStateOf(false) }
    var expandedImageUrl by remember { mutableStateOf<String?>(null) }

    val mediaPlayer = remember {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
        }
    }
    var currentlyPlayingId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { mediaPlayer.release() }
    }

    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioTempFile by remember { mutableStateOf<File?>(null) }

    fun stopRecordingAndSend() {
        if (!isRecording) return
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {}

        mediaRecorder?.release()
        mediaRecorder = null
        isRecording = false
        isUploading = true
        errorMessage = null

        scope.launch {
            try {
                val bytes = audioTempFile?.readBytes()
                if (bytes != null) {
                    val fileName = "${UUID.randomUUID()}.m4a"
                    SupabaseClient.client.storage.from("resenha").upload(fileName, bytes)
                    val publicUrl = SupabaseClient.client.storage.from("resenha").publicUrl(fileName)

                    val timeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                    timeFormat.timeZone = TimeZone.getTimeZone("UTC")
                    val currentTime = timeFormat.format(Date())

                    SupabaseClient.client.from("messages").insert(
                        mapOf(
                            "conversation_id" to conversationId,
                            "sender_id" to currentUserId,
                            "content" to "🎤 Mensagem de Voz",
                            "status" to "enviada",
                            "media_url" to publicUrl,
                            "media_type" to "audio"
                        )
                    )
                    SupabaseClient.client.from("conversations").update(
                        {
                            set("last_message", "🎤 Mensagem de Voz")
                            set("last_message_sender_id", currentUserId)
                            set("last_message_status", "enviada")
                            set("last_message_time", currentTime)
                        }
                    ) { filter { eq("id", conversationId) } }

                    audioTempFile?.delete()
                }
            } catch (e: Exception) {
                errorMessage = "Erro ao enviar voz: ${e.message}"
            } finally {
                isUploading = false
            }
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            try {
                val file = File(context.cacheDir, "voz_${UUID.randomUUID()}.m4a")
                audioTempFile = file
                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(file.absolutePath)
                    prepare()
                    start()
                }
                isRecording = true
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = "Erro ao iniciar o microfone: ${e.message}"
            }
        } else {
            errorMessage = "Permissão de microfone negada."
        }
    }

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

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isUploading = true
            errorMessage = null
            scope.launch {
                try {
                    val mimeType = context.contentResolver.getType(uri) ?: ""
                    val isImage = mimeType.startsWith("image/")
                    val isPdf = mimeType == "application/pdf"
                    val isAudio = mimeType.startsWith("audio/") || mimeType == "application/ogg"
                    val isVideo = mimeType.startsWith("video/")

                    if (!isImage && !isPdf && !isAudio && !isVideo) {
                        errorMessage = "Apenas Imagens, Vídeos, PDFs ou Áudios são suportados."
                        isUploading = false
                        return@launch
                    }

                    val fileDescriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
                    val fileSize = fileDescriptor?.length ?: 0L
                    fileDescriptor?.close()

                    val fileSizeInMB = fileSize / (1024.0 * 1024.0)
                    if (fileSizeInMB > 15.0) {
                        val sizeStr = (fileSizeInMB * 10.0).toInt() / 10.0
                        // MENSAGEM MAIS AMIGÁVEL
                        errorMessage = "Oops! O arquivo tem ${sizeStr} MB. Para não estourar o servidor, envie arquivos de até 15 MB! 😅"
                        isUploading = false
                        return@launch
                    }

                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        val extension = when {
                            isImage -> ".jpg"
                            isPdf -> ".pdf"
                            isAudio -> ".mp3"
                            else -> ".mp4"
                        }
                        val fileName = "${UUID.randomUUID()}$extension"

                        SupabaseClient.client.storage.from("resenha").upload(fileName, bytes)
                        val publicUrl = SupabaseClient.client.storage.from("resenha").publicUrl(fileName)

                        val timeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                        timeFormat.timeZone = TimeZone.getTimeZone("UTC")
                        val currentTime = timeFormat.format(Date())

                        val contentStr = when {
                            isImage -> "📷 Imagem"
                            isPdf -> "📄 Arquivo PDF"
                            isAudio -> "🎵 Áudio"
                            else -> "🎥 Vídeo"
                        }
                        val typeStr = when {
                            isImage -> "image"
                            isPdf -> "pdf"
                            isAudio -> "audio"
                            else -> "video"
                        }

                        SupabaseClient.client.from("messages").insert(
                            mapOf(
                                "conversation_id" to conversationId,
                                "sender_id" to currentUserId,
                                "content" to contentStr,
                                "status" to "enviada",
                                "media_url" to publicUrl,
                                "media_type" to typeStr
                            )
                        )
                        SupabaseClient.client.from("conversations").update(
                            {
                                set("last_message", contentStr)
                                set("last_message_sender_id", currentUserId)
                                set("last_message_status", "enviada")
                                set("last_message_time", currentTime)
                            }
                        ) { filter { eq("id", conversationId) } }

                        recarregarMensagens()
                    }
                } catch (e: Exception) {
                    errorMessage = "Erro ao enviar arquivo: ${e.message}"
                } finally {
                    isUploading = false
                }
            }
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
                            Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {

                                if (msg.media_url != null) {
                                    if (msg.media_type == "pdf") {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth(0.8f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Black.copy(alpha = 0.1f))
                                                .clickable { uriHandler.openUri(msg.media_url) }
                                                .padding(12.dp)
                                        ) {
                                            Text("📄", fontSize = 28.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Documento PDF\nToque para abrir",
                                                color = if (isMine) Color.White else Color.Black,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                    } else if (msg.media_type == "video") {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth(0.8f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Black.copy(alpha = 0.1f))
                                                .clickable { uriHandler.openUri(msg.media_url) }
                                                .padding(12.dp)
                                        ) {
                                            Text("🎥", fontSize = 28.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Vídeo\nToque para reproduzir",
                                                color = if (isMine) Color.White else Color.Black,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                    } else if (msg.media_type == "audio") {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth(0.6f)
                                                .clip(RoundedCornerShape(24.dp))
                                                .background(if (isMine) Color.White.copy(alpha = 0.2f) else blueColor.copy(alpha = 0.1f))
                                                .padding(4.dp)
                                        ) {
                                            val isPlaying = currentlyPlayingId == msg.id
                                            IconButton(onClick = {
                                                if (isPlaying) {
                                                    mediaPlayer.pause()
                                                    currentlyPlayingId = null
                                                } else {
                                                    try {
                                                        mediaPlayer.reset()
                                                        mediaPlayer.setOnErrorListener { _, _, _ ->
                                                            errorMessage = "Formato não suportado."
                                                            currentlyPlayingId = null
                                                            true
                                                        }
                                                        mediaPlayer.setDataSource(msg.media_url)
                                                        mediaPlayer.prepareAsync()
                                                        mediaPlayer.setOnPreparedListener {
                                                            it.start()
                                                            currentlyPlayingId = msg.id
                                                        }
                                                        mediaPlayer.setOnCompletionListener {
                                                            currentlyPlayingId = null
                                                        }
                                                    } catch (e: Exception) {
                                                        errorMessage = "Erro ao iniciar o áudio: ${e.message}"
                                                    }
                                                }
                                            }) {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = "Play",
                                                    tint = if (isMine) Color.White else blueColor
                                                )
                                            }
                                            Text(
                                                text = if (isPlaying) "A reproduzir..." else "Áudio",
                                                color = if (isMine) Color.White else Color.Black,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                    } else {
                                        AsyncImage(
                                            model = msg.media_url,
                                            contentDescription = "Imagem anexada",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth(0.7f)
                                                .height(200.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { expandedImageUrl = msg.media_url }
                                                .padding(bottom = 8.dp)
                                        )
                                    }
                                }

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
            }

            // --- NOVO BANNER DE ERRO ELEGANTE ---
            if (errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Aviso",
                            tint = Color(0xFFD32F2F)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFFD32F2F),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { errorMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar",
                                tint = Color(0xFFD32F2F)
                            )
                        }
                    }
                }
            }
            // ------------------------------------

            Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isUploading) {
                    Spacer(modifier = Modifier.weight(1f))
                    CircularProgressIndicator(modifier = Modifier.size(48.dp).padding(8.dp), color = blueColor)
                    Spacer(modifier = Modifier.weight(1f))
                } else if (isRecording) {
                    Text(
                        text = "A gravar áudio...",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 16.dp)
                    )
                    IconButton(
                        onClick = { stopRecordingAndSend() },
                        modifier = Modifier.background(Color.Red, CircleShape)
                    ) {
                        Icon(Icons.Default.Stop, null, tint = Color.White)
                    }
                } else {
                    IconButton(onClick = { fileLauncher.launch("*/*") }) {
                        Icon(Icons.Default.AttachFile, null, tint = blueColor)
                    }

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Mensagem...", color = Color.Black) },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.Black, unfocusedTextColor = Color.Black, cursorColor = Color.Black, focusedBorderColor = blueColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    if (messageText.trim().isEmpty()) {
                        IconButton(
                            onClick = {
                                val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    try {
                                        val file = File(context.cacheDir, "voz_${UUID.randomUUID()}.m4a")
                                        audioTempFile = file
                                        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            MediaRecorder(context)
                                        } else {
                                            @Suppress("DEPRECATION")
                                            MediaRecorder()
                                        }.apply {
                                            setAudioSource(MediaRecorder.AudioSource.MIC)
                                            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                            setOutputFile(file.absolutePath)
                                            prepare()
                                            start()
                                        }
                                        isRecording = true
                                    } catch (e: Exception) {
                                        errorMessage = "Erro ao gravar: ${e.message}"
                                    }
                                } else {
                                    recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.background(blueColor.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.Mic, null, tint = blueColor)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                val textToSend = messageText
                                messageText = ""
                                errorMessage = null
                                scope.launch {
                                    try {
                                        val isImageUrl = textToSend.startsWith("http") &&
                                                (textToSend.endsWith(".jpg", ignoreCase = true) ||
                                                        textToSend.endsWith(".png", ignoreCase = true) ||
                                                        textToSend.endsWith(".jpeg", ignoreCase = true) ||
                                                        textToSend.endsWith(".gif", ignoreCase = true) ||
                                                        textToSend.endsWith(".webp", ignoreCase = true))

                                        val isPdfUrl = textToSend.startsWith("http") && textToSend.endsWith(".pdf", ignoreCase = true)

                                        val isAudioUrl = textToSend.startsWith("http") &&
                                                (textToSend.endsWith(".mp3", ignoreCase = true) ||
                                                        textToSend.endsWith(".wav", ignoreCase = true) ||
                                                        textToSend.endsWith(".ogg", ignoreCase = true) ||
                                                        textToSend.endsWith(".m4a", ignoreCase = true))

                                        val isVideoUrl = textToSend.startsWith("http") &&
                                                (textToSend.endsWith(".mp4", ignoreCase = true) ||
                                                        textToSend.endsWith(".mov", ignoreCase = true) ||
                                                        textToSend.endsWith(".mkv", ignoreCase = true) ||
                                                        textToSend.endsWith(".webm", ignoreCase = true))

                                        val contentToSave = when {
                                            isImageUrl -> "📷 Imagem (Link)"
                                            isPdfUrl -> "📄 PDF (Link)"
                                            isAudioUrl -> "🎵 Áudio (Link)"
                                            isVideoUrl -> "🎥 Vídeo (Link)"
                                            else -> textToSend
                                        }
                                        val mediaUrlToSave = if (isImageUrl || isPdfUrl || isAudioUrl || isVideoUrl) textToSend else null
                                        val mediaTypeToSave = when {
                                            isImageUrl -> "image"
                                            isPdfUrl -> "pdf"
                                            isAudioUrl -> "audio"
                                            isVideoUrl -> "video"
                                            else -> null
                                        }

                                        val timeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                                        timeFormat.timeZone = TimeZone.getTimeZone("UTC")
                                        val currentTime = timeFormat.format(Date())

                                        SupabaseClient.client.from("messages").insert(
                                            mapOf(
                                                "conversation_id" to conversationId,
                                                "sender_id" to currentUserId,
                                                "content" to contentToSave,
                                                "status" to "enviada",
                                                "media_url" to mediaUrlToSave,
                                                "media_type" to mediaTypeToSave
                                            )
                                        )
                                        SupabaseClient.client.from("conversations").update(
                                            {
                                                set("last_message", contentToSave)
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
                            },
                            modifier = Modifier.background(blueColor, CircleShape)
                        ) {
                            Icon(Icons.Default.Send, null, tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (expandedImageUrl != null) {
        Dialog(
            onDismissRequest = { expandedImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { expandedImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = expandedImageUrl,
                    contentDescription = "Imagem expandida",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}