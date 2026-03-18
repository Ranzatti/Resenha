package com.example.resenha.data

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
@OptIn(InternalSerializationApi::class)
@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val profile_image_url: String? = null,
    val status: String = "offline"
)


data class ChatItemUiState(
    val conversation: Conversation,
    val contactName: String,
    val unreadCount: Int
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class MessageBadge(
    val id: String,
    val conversation_id: String,
    val sender_id: String,
    val content: String? = null,
    val created_at: String? = null,
    val status: String = "enviada",
    val is_pinned: Boolean = false
)


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
    val is_pinned: Boolean = false,
    val is_group: Boolean = false,
    val name: String? = null,
    val group_image_url: String? = null
)