package com.example.resenha.data

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val profile_image_url: String? = null,
    val status: String = "offline"
)

@Serializable
data class Chat(
    val id: String,
    val is_group: Boolean = false,
    val name: String? = null,
    val group_image_url: String? = null
)

@Serializable
data class Message(
    val id: String? = null, // O Supabase gera o ID automaticamente
    val chat_id: String,
    val sender_id: String,
    val content: String? = null,
    val media_url: String? = null,
    val media_type: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val is_pinned: Boolean = false,
    val created_at: String? = null
)