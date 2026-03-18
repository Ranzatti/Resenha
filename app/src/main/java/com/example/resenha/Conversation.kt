package com.example.resenha.data

import android.annotation.SuppressLint
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.InternalSerializationApi

@OptIn(InternalSerializationApi::class)
@Serializable
data class Conversation(
    val id: String,
    val user_1: String? = null,
    val user_2: String? = null,

    val last_message: String? = null,
    val last_message_sender_id: String? = null,
    val last_message_status: String? = "enviada",
    val last_message_time: String? = null,
    @SerialName("is_group") val isGroup: Boolean = false,
    val name: String? = null,
    val group_image_url: String? = null,
)
// Opção B: manter id para leituras, mas não serializar no insert
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
@Serializable
data class ConversationParticipant(
    @EncodeDefault(EncodeDefault.Mode.NEVER) // null não vai no JSON
    val id: String? = null,
    val conversation_id: String,
    val user_id: String,
    val role: String? = null
)
