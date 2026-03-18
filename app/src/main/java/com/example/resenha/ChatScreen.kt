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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.resenha.data.ChatMessage
import com.example.resenha.data.Conversation
import com.example.resenha.data.ConversationParticipant
import com.example.resenha.data.UserProfile
import com.example.resenha.network.SupabaseClient
import com.example.resenha.ui.group.GroupManagementScreen
import com.google.android.gms.location.LocationServices
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, InternalSerializationApi::class)
@Composable
fun ChatScreen(conversationId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val blueColor = Color(0xFF94ADFF)
    var messageText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var contactName by remember { mutableStateOf("Carregando...") }

    val currentUserId = remember { SupabaseClient.client.auth.currentUserOrNull()?.id ?: "" }

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var isUploading by remember { mutableStateOf(false) }
    var expandedImageUrl by remember { mutableStateOf<String?>(null) }
    var showAttachmentMenu by remember { mutableStateOf(false) }

    var showDeleteDialog by remember { mutableStateOf(false) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    var contactImageUrl by remember { mutableStateOf<String?>(null) }
    var showGroupManagement by remember { mutableStateOf(false) }
    var isGroup by remember { mutableStateOf(false) }
    var members by remember { mutableStateOf(listOf<UserProfile>()) }

    fun formatHeaderDate(rawTime: String?): String {
        if (rawTime.isNullOrEmpty()) return "Desconhecido"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val cleanTime = rawTime.substringBefore(".").substringBefore("+").substringBefore("Z")
            val date = parser.parse(cleanTime) ?: return "Desconhecido"

            val now = Date()
            val fmtDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

            when (fmtDay.format(date)) {
                fmtDay.format(now) -> "Hoje"
                fmtDay.format(Date(now.time - 86400000)) -> "Ontem"
                else -> SimpleDateFormat("dd 'de' MMMM", Locale("pt", "BR")).format(date)
            }
        } catch (e: Exception) {
            "Data Inválida"
        }
    }

    fun formatMessageTime(rawTime: String?): String {
        if (rawTime.isNullOrEmpty()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val cleanTime = rawTime.substringBefore(".").substringBefore("+").substringBefore("Z")
            val date = parser.parse(cleanTime) ?: return ""

            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            formatter.timeZone = TimeZone.getDefault()
            formatter.format(date)
        } catch (e: Exception) {
            ""
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !isSearchActive) {
            listState.animateScrollToItem(0)
        }
    }

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

    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioTempFile by remember { mutableStateOf<File?>(null) }
    val locationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun recarregarMensagens() {
        scope.launch {
            try {
                val msgs = SupabaseClient.client.from("messages")
                    .select { filter { eq("conversation_id", conversationId) } }
                    .decodeList<ChatMessage>()

                messages = msgs.map { it.copy(content = CryptoUtils.decrypt(it.content)) }.sortedBy { it.created_at }

                val mensagensNaoLidas =
                    msgs.filter { it.sender_id != currentUserId && it.status != "lida" }
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
            } catch (e: Exception) {
            }
        }
    }

    val takePictureLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && cameraImageUri != null) {
                isUploading = true
                errorMessage = null
                scope.launch {
                    try {
                        val bytes = context.contentResolver.openInputStream(cameraImageUri!!)
                            ?.use { it.readBytes() }
                        if (bytes != null) {
                            val fileName = "${UUID.randomUUID()}.jpg"

                            SupabaseClient.client.storage.from("resenha").upload(fileName, bytes)
                            val publicUrl =
                                SupabaseClient.client.storage.from("resenha").publicUrl(fileName)

                            val timeFormat = SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                                Locale.getDefault()
                            )
                            timeFormat.timeZone = TimeZone.getTimeZone("UTC")
                            val currentTime = timeFormat.format(Date())

                            val contentStr = "📷 Imagem da Câmera"

                            SupabaseClient.client.from("messages").insert(
                                mapOf(
                                    "conversation_id" to conversationId,
                                    "sender_id" to currentUserId,
                                    "content" to CryptoUtils.encrypt(contentStr),
                                    "status" to "enviada",
                                    "media_url" to publicUrl,
                                    "media_type" to "image"
                                )
                            )
                            SupabaseClient.client.from("conversations").update(
                                {
                                    set("last_message", CryptoUtils.encrypt(contentStr))
                                    set("last_message_sender_id", currentUserId)
                                    set("last_message_status", "enviada")
                                    set("last_message_time", currentTime)
                                }
                            ) { filter { eq("id", conversationId) } }

                            recarregarMensagens()
                        }
                    } catch (e: Exception) {
                        errorMessage = "Erro ao enviar foto da câmera: ${e.message}"
                    } finally {
                        isUploading = false
                    }
                }
            }
        }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                try {
                    val imagesFolder = File(context.cacheDir, "camera_images")
                    imagesFolder.mkdirs()
                    val tempImageFile = File(imagesFolder, "foto_${UUID.randomUUID()}.jpg")

                    val uri = FileProvider.getUriForFile(
                        context,
                        context.packageName + ".provider",
                        tempImageFile
                    )
                    cameraImageUri = uri
                    takePictureLauncher.launch(uri)
                } catch (e: Exception) {
                    errorMessage = "Erro ao abrir câmera: ${e.message}"
                }
            } else {
                errorMessage = "Permissão de câmera negada."
            }
        }

    fun stopRecordingAndSend() {
        if (!isRecording) return
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
        }

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
                    val publicUrl =
                        SupabaseClient.client.storage.from("resenha").publicUrl(fileName)

                    val timeFormat =
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                    timeFormat.timeZone = TimeZone.getTimeZone("UTC")
                    val currentTime = timeFormat.format(Date())

                    val contentStr = "🎤 Mensagem de Voz"

                    SupabaseClient.client.from("messages").insert(
                        mapOf(
                            "conversation_id" to conversationId,
                            "sender_id" to currentUserId,
                            "content" to CryptoUtils.encrypt(contentStr),
                            "status" to "enviada",
                            "media_url" to publicUrl,
                            "media_type" to "audio"
                        )
                    )
                    SupabaseClient.client.from("conversations").update(
                        {
                            set("last_message", CryptoUtils.encrypt(contentStr))
                            set("last_message_sender_id", currentUserId)
                            set("last_message_status", "enviada")
                            set("last_message_time", currentTime)
                        }
                    ) { filter { eq("id", conversationId) } }

                    audioTempFile?.delete()
                    recarregarMensagens()
                }
            } catch (e: Exception) {
                errorMessage = "Erro ao enviar voz: ${e.message}"
            } finally {
                isUploading = false
            }
        }
    }

    val recordPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
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

    fun sendLocationMessage(lat: Double, lon: Double) {
        val mapsLink = "https://maps.google.com/?q=$lat,$lon"
        scope.launch {
            try {
                val timeFormat =
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                timeFormat.timeZone = TimeZone.getTimeZone("UTC")
                val currentTime = timeFormat.format(Date())

                val contentStr = "📍 Localização Atual"

                SupabaseClient.client.from("messages").insert(
                    mapOf(
                        "conversation_id" to conversationId,
                        "sender_id" to currentUserId,
                        "content" to CryptoUtils.encrypt(contentStr),
                        "status" to "enviada",
                        "media_url" to mapsLink,
                        "media_type" to "location"
                    )
                )
                SupabaseClient.client.from("conversations").update(
                    {
                        set("last_message", CryptoUtils.encrypt(contentStr))
                        set("last_message_sender_id", currentUserId)
                        set("last_message_status", "enviada")
                        set("last_message_time", currentTime)
                    }
                ) { filter { eq("id", conversationId) } }

                recarregarMensagens()
            } catch (e: Exception) {
                errorMessage = "Erro ao enviar localização: ${e.message}"
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            try {
                locationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        sendLocationMessage(location.latitude, location.longitude)
                    } else {
                        errorMessage =
                            "GPS não encontrou sua posição. Abra o Google Maps uma vez e tente novamente."
                    }
                }
            } catch (e: SecurityException) {
                errorMessage = "Erro de segurança ao acessar GPS."
            }
        } else {
            errorMessage = "Permissão de localização negada."
        }
    }

    val fileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
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

                        val fileDescriptor =
                            context.contentResolver.openAssetFileDescriptor(uri, "r")
                        val fileSize = fileDescriptor?.length ?: 0L
                        fileDescriptor?.close()

                        val fileSizeInMB = fileSize / (1024.0 * 1024.0)
                        if (fileSizeInMB > 15.0) {
                            val sizeStr = (fileSizeInMB * 10.0).toInt() / 10.0
                            errorMessage =
                                "Oops! O arquivo tem ${sizeStr} MB. O limite de envio é 15 MB! 😅"
                            isUploading = false
                            return@launch
                        }

                        val bytes =
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes != null) {
                            val extension = when {
                                isImage -> ".jpg"
                                isPdf -> ".pdf"
                                isAudio -> ".mp3"
                                else -> ".mp4"
                            }
                            val fileName = "${UUID.randomUUID()}$extension"

                            SupabaseClient.client.storage.from("resenha").upload(fileName, bytes)
                            val publicUrl =
                                SupabaseClient.client.storage.from("resenha").publicUrl(fileName)

                            val timeFormat = SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                                Locale.getDefault()
                            )
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
                                    "content" to CryptoUtils.encrypt(contentStr),
                                    "status" to "enviada",
                                    "media_url" to publicUrl,
                                    "media_type" to typeStr
                                )
                            )
                            SupabaseClient.client.from("conversations").update(
                                {
                                    set("last_message", CryptoUtils.encrypt(contentStr))
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
            repeat(10) { attempt ->
                try {
                    val convs = SupabaseClient.client.from("conversations")
                        .select { filter { eq("id", conversationId) } }
                        .decodeList<Conversation>()

                    val conv = convs.firstOrNull()
                    if (conv != null) {
                        isGroup = conv.is_group

                        if (conv.is_group) {
                            contactName = conv.name ?: "Grupo Sem Nome"
                            contactImageUrl = conv.group_image_url
                        } else {
                            val u1 = conv.user_1 ?: ""
                            val u2 = conv.user_2 ?: ""
                            val otherId = if (u1 == currentUserId) u2 else u1

                            if (otherId.isNotEmpty()) {
                                val otherUsers = SupabaseClient.client.from("users")
                                    .select { filter { eq("id", otherId) } }
                                    .decodeList<UserProfile>()

                                val otherUser = otherUsers.firstOrNull()
                                contactName = otherUser?.name ?: "Resenha"
                                contactImageUrl = otherUser?.profile_image_url
                            }
                        }
                        return@launch
                    }
                } catch (e: Exception) {
                }

                contactName = "Carregando... (${attempt + 1}/10)"
                kotlinx.coroutines.delay(800)
            }
            contactName = "Erro: conversa não encontrada"
        }

        scope.launch {
            try {
                val channel = SupabaseClient.client.channel("chat-$conversationId")
                val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public")

                launch { changeFlow.collect { recarregarMensagens() } }
                channel.subscribe()
            } catch (e: Exception) {
            }
        }

        scope.launch {
            while (true) {
                delay(3000)
                recarregarMensagens()
            }
        }
    }

    LaunchedEffect(conversationId, isGroup) {
        if (isGroup) {
            scope.launch {
                val participants = SupabaseClient.client.from("conversation_participants")
                    .select { filter { eq("conversation_id", conversationId) } }
                    .decodeList<ConversationParticipant>()

                members = participants.mapNotNull { participant ->
                    try {
                        SupabaseClient.client.from("users")
                            .select { filter { eq("id", participant.user_id) } }
                            .decodeSingle<UserProfile>()
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "Eliminar Resenha",
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            },
            text = {
                Text(
                    text = "Tem a certeza que deseja apagar esta conversa para sempre? Todas as mensagens e os seus ficheiros de multimédia serão eliminados.",
                    color = Color.DarkGray
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    isUploading = true

                    scope.launch {
                        try {
                            val myMediaMsgs = SupabaseClient.client.from("messages")
                                .select {
                                    filter {
                                        eq("conversation_id", conversationId)
                                        eq("sender_id", currentUserId)
                                    }
                                }
                                .decodeList<ChatMessage>()

                            val mediaUrls = myMediaMsgs.mapNotNull { it.media_url }
                                .filter { it.contains("supabase.co/storage/v1/object/public/resenha/") }

                            if (mediaUrls.isNotEmpty()) {
                                val fileNames = mediaUrls.map { it.substringAfterLast("/") }
                                try {
                                    SupabaseClient.client.storage.from("resenha").delete(fileNames)
                                } catch (e: Exception) {
                                }
                            }

                            try {
                                SupabaseClient.client.from("messages").delete {
                                    filter { eq("conversation_id", conversationId) }
                                }
                            } catch (e: Exception) {
                            }

                            SupabaseClient.client.from("conversations").delete {
                                filter { eq("id", conversationId) }
                            }

                            isUploading = false
                            onBack()
                        } catch (e: Exception) {
                            isUploading = false
                            errorMessage =
                                "Erro ao eliminar: Certifique-se de que ativou o Cascade no Supabase!"
                        }
                    }
                }) { Text("Eliminar Tudo", color = Color.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(
                        "Cancelar",
                        color = Color.Black
                    )
                }
            },
            containerColor = Color.White
        )
    }

    val displayedMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) {
            messages
        } else {
            messages.filter { it.content.contains(searchQuery, ignoreCase = true) }
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
                            placeholder = { Text("Buscar na conversa...", color = Color.Gray) },
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
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                    .data(
                                        contactImageUrl
                                            ?: "https://ui-avatars.com/api/?name=$contactName&background=94ADFF&color=fff"
                                    )
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.LightGray),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                contactName,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                null,
                                tint = Color.Black
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Color.Black)
                        }

                        if (isGroup) {
                            IconButton(
                                onClick = { showGroupManagement = true }
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Gerenciar grupo",
                                    tint = Color.Black
                                )
                            }
                        }

                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Excluir Conversa", tint = Color.Red)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        },
        containerColor = Color(0xFFF5F7FF)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val groupedMessages = displayedMessages.groupBy { formatHeaderDate(it.created_at) }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                reverseLayout = true
            ) {
                groupedMessages.entries.reversed().forEach { (dateHeader, msgsForDate) ->
                    items(msgsForDate.reversed(), key = { it.id }) { msg ->
                        val isMine = msg.sender_id == currentUserId

                        // --- NOVA LÓGICA DE AVATAR DO REMETENTE ---
                        val senderProfile = if (isGroup) members.find { it.id == msg.sender_id } else null
                        val displayAvatarUrl = if (isGroup) {
                            senderProfile?.profile_image_url ?: "https://ui-avatars.com/api/?name=${senderProfile?.name ?: "U"}&background=94ADFF&color=fff"
                        } else {
                            contactImageUrl ?: "https://ui-avatars.com/api/?name=$contactName&background=94ADFF&color=fff"
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            if (!isMine) {
                                AsyncImage(
                                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                                        .data(displayAvatarUrl) // FOTO CORRIGIDA AQUI
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color.LightGray),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .background(
                                        color = if (isMine) blueColor else Color.White,
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (isMine) 16.dp else 4.dp,
                                            bottomEnd = if (isMine) 4.dp else 16.dp
                                        )
                                    )
                                    .padding(12.dp)
                            ) {
                                Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {

                                    // --- BÔNUS: NOME DO REMETENTE NOS GRUPOS ---
                                    if (isGroup && !isMine) {
                                        val senderNameStr = senderProfile?.name ?: "Usuário"
                                        Text(
                                            text = senderNameStr,
                                            color = blueColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                    }

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
                                        } else if (msg.media_type == "location") {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth(0.8f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.Black.copy(alpha = 0.1f))
                                                    .clickable { uriHandler.openUri(msg.media_url) }
                                                    .padding(12.dp)
                                            ) {
                                                Text("📍", fontSize = 28.sp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Localização Atual\nToque para abrir no mapa",
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
                                                    .background(
                                                        if (isMine) Color.White.copy(alpha = 0.2f) else blueColor.copy(
                                                            alpha = 0.1f
                                                        )
                                                    )
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
                                                                errorMessage =
                                                                    "Formato não suportado."
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
                                                            errorMessage =
                                                                "Erro ao iniciar o áudio: ${e.message}"
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
                                            fontSize = 16.sp,
                                            lineHeight = 22.sp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = formatMessageTime(msg.created_at),
                                                color = if (isMine) Color.White.copy(alpha = 0.7f) else Color.Gray,
                                                fontSize = 10.sp
                                            )

                                            if (isMine) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                val icon =
                                                    if (msg.status == "enviada") Icons.Default.Check else Icons.Default.DoneAll
                                                val iconTint =
                                                    if (msg.status == "lida") Color(0xFF4CAF50) else Color.White.copy(
                                                        alpha = 0.8f
                                                    )
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = iconTint
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dateHeader,
                                color = Color.DarkGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .background(
                                        Color.Black.copy(alpha = 0.05f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }


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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(8.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                if (isUploading) {
                    Spacer(modifier = Modifier.weight(1f))
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(48.dp)
                            .padding(8.dp), color = blueColor
                    )
                    Spacer(modifier = Modifier.weight(1f))
                } else if (isRecording) {
                    Text(
                        text = "A gravar áudio...",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    )
                    IconButton(
                        onClick = { stopRecordingAndSend() },
                        modifier = Modifier.background(Color.Red, CircleShape)
                    ) {
                        Icon(Icons.Default.Stop, null, tint = Color.White)
                    }
                } else {

                    Box {
                        IconButton(onClick = { showAttachmentMenu = true }) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = "Anexos",
                                tint = blueColor
                            )
                        }

                        DropdownMenu(
                            expanded = showAttachmentMenu,
                            onDismissRequest = { showAttachmentMenu = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Galeria / Arquivos", color = Color.Black) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = null,
                                        tint = blueColor
                                    )
                                },
                                onClick = {
                                    showAttachmentMenu = false
                                    fileLauncher.launch("*/*")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Câmera", color = Color.Black) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.PhotoCamera,
                                        contentDescription = null,
                                        tint = blueColor
                                    )
                                },
                                onClick = {
                                    showAttachmentMenu = false
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        try {
                                            val imagesFolder =
                                                File(context.cacheDir, "camera_images")
                                            imagesFolder.mkdirs()
                                            val tempImageFile =
                                                File(imagesFolder, "foto_${UUID.randomUUID()}.jpg")

                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                context.packageName + ".provider",
                                                tempImageFile
                                            )
                                            cameraImageUri = uri
                                            takePictureLauncher.launch(uri)
                                        } catch (e: Exception) {
                                            errorMessage =
                                                "Erro ao abrir câmera. Verifique o FileProvider."
                                        }
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Localização Atual", color = Color.Black) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = blueColor
                                    )
                                },
                                onClick = {
                                    showAttachmentMenu = false
                                    val hasFineLocation = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                    val hasCoarseLocation = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (hasFineLocation || hasCoarseLocation) {
                                        try {
                                            locationClient.lastLocation.addOnSuccessListener { location ->
                                                if (location != null) {
                                                    sendLocationMessage(
                                                        location.latitude,
                                                        location.longitude
                                                    )
                                                } else {
                                                    errorMessage =
                                                        "GPS não encontrou sua posição. Abra o Google Maps uma vez e tente novamente."
                                                }
                                            }
                                        } catch (e: SecurityException) {
                                            errorMessage = "Erro ao acessar o GPS."
                                        }
                                    } else {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Mensagem...", color = Color.Black) },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            cursorColor = Color.Black,
                            focusedBorderColor = blueColor
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    if (messageText.trim().isEmpty()) {
                        IconButton(
                            onClick = {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    try {
                                        val file =
                                            File(context.cacheDir, "voz_${UUID.randomUUID()}.m4a")
                                        audioTempFile = file
                                        mediaRecorder =
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
                            modifier = Modifier.background(
                                blueColor.copy(alpha = 0.2f),
                                CircleShape
                            )
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
                                                        textToSend.endsWith(
                                                            ".png",
                                                            ignoreCase = true
                                                        ) ||
                                                        textToSend.endsWith(
                                                            ".jpeg",
                                                            ignoreCase = true
                                                        ) ||
                                                        textToSend.endsWith(
                                                            ".gif",
                                                            ignoreCase = true
                                                        ) ||
                                                        textToSend.endsWith(
                                                            ".webp",
                                                            ignoreCase = true
                                                        ))

                                        val isPdfUrl =
                                            textToSend.startsWith("http") && textToSend.endsWith(
                                                ".pdf",
                                                ignoreCase = true
                                            )

                                        val isAudioUrl = textToSend.startsWith("http") &&
                                                (textToSend.endsWith(".mp3", ignoreCase = true) ||
                                                        textToSend.endsWith(
                                                            ".wav",
                                                            ignoreCase = true
                                                        ) ||
                                                        textToSend.endsWith(
                                                            ".ogg",
                                                            ignoreCase = true
                                                        ) ||
                                                        textToSend.endsWith(
                                                            ".m4a",
                                                            ignoreCase = true
                                                        ))

                                        val isVideoUrl = textToSend.startsWith("http") &&
                                                (textToSend.endsWith(".mp4", ignoreCase = true) ||
                                                        textToSend.endsWith(
                                                            ".mov",
                                                            ignoreCase = true
                                                        ) ||
                                                        textToSend.endsWith(
                                                            ".mkv",
                                                            ignoreCase = true
                                                        ) ||
                                                        textToSend.endsWith(
                                                            ".webm",
                                                            ignoreCase = true
                                                        ))

                                        val contentToSave = when {
                                            isImageUrl -> "📷 Imagem (Link)"
                                            isPdfUrl -> "📄 PDF (Link)"
                                            isAudioUrl -> "🎵 Áudio (Link)"
                                            isVideoUrl -> "🎥 Vídeo (Link)"
                                            else -> textToSend
                                        }
                                        val mediaUrlToSave =
                                            if (isImageUrl || isPdfUrl || isAudioUrl || isVideoUrl) textToSend else null
                                        val mediaTypeToSave = when {
                                            isImageUrl -> "image"
                                            isPdfUrl -> "pdf"
                                            isAudioUrl -> "audio"
                                            isVideoUrl -> "video"
                                            else -> null
                                        }

                                        val timeFormat = SimpleDateFormat(
                                            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                                            Locale.getDefault()
                                        )
                                        timeFormat.timeZone = TimeZone.getTimeZone("UTC")
                                        val currentTime = timeFormat.format(Date())

                                        SupabaseClient.client.from("messages").insert(
                                            mapOf(
                                                "conversation_id" to conversationId,
                                                "sender_id" to currentUserId,
                                                "content" to CryptoUtils.encrypt(contentToSave),
                                                "status" to "enviada",
                                                "media_url" to mediaUrlToSave,
                                                "media_type" to mediaTypeToSave
                                            )
                                        )
                                        SupabaseClient.client.from("conversations").update(
                                            {
                                                set(
                                                    "last_message",
                                                    CryptoUtils.encrypt(contentToSave)
                                                )
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

    if (showGroupManagement && isGroup) {
        Dialog(onDismissRequest = { showGroupManagement = false }) {
            Card(Modifier.padding(16.dp)) {
                GroupManagementScreen(
                    conversationId = conversationId,
                    currentName = contactName,
                    currentImageUrl = contactImageUrl,
                    onNameUpdated = { contactName = it },
                    onImageChanged = { contactImageUrl = it },
                    onBack = { showGroupManagement = false }
                )
            }
        }
    }
}