package com.example.resenha

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resenha.network.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.InternalSerializationApi

@OptIn(InternalSerializationApi::class)
@Serializable
data class UserProfile(
    val id: String,
    val name: String
)

@OptIn(ExperimentalMaterial3Api::class, InternalSerializationApi::class)
@Composable
fun SearchUsersScreen(onBack: () -> Unit, onUserSelected: (UserProfile) -> Unit) {
    val scope = rememberCoroutineScope()
    val blueColor = Color(0xFF94ADFF)
    var searchQuery by remember { mutableStateOf("") }
    var usersList by remember { mutableStateOf(listOf<UserProfile>()) }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buscar Pessoas", fontWeight = FontWeight.Bold, color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.Black)
                    }
                }
            )
        },
        containerColor = Color(0xFFF5F7FF)
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { query ->
                    searchQuery = query
                    if (query.trim().isNotEmpty()) {
                        isLoading = true
                        scope.launch {
                            try {
                                val result = SupabaseClient.client.from("users")
                                    .select {
                                        filter { ilike("name", "%${query.trim()}%") }
                                    }.decodeList<UserProfile>()
                                usersList = result
                            } catch (e: Exception) {
                                usersList = emptyList()
                            } finally {
                                isLoading = false
                            }
                        }
                    } else {
                        usersList = emptyList()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Digite o nome da pessoa...", color = Color.Black) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = blueColor) },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    cursorColor = Color.Black,
                    focusedBorderColor = blueColor
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = blueColor)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(usersList) { user ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onUserSelected(user) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(44.dp).background(blueColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(user.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(user.name, fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}