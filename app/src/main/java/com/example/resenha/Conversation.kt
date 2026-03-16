    package com.example.resenha.data

    import kotlinx.serialization.Serializable
    import kotlinx.serialization.InternalSerializationApi

    @OptIn(InternalSerializationApi::class)
    @Serializable
    data class Conversation(
        val id: String,
        val user_1: String,
        val user_2: String,
        val last_message: String? = null,
        val last_message_sender_id: String? = null,
        val last_message_status: String? = "enviada",
        val last_message_time: String? = null,
        val is_pinned: Boolean = false
    )