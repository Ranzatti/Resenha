    package com.example.resenha.network

    import io.github.jan.supabase.auth.Auth
    import io.github.jan.supabase.createSupabaseClient
    import io.github.jan.supabase.postgrest.Postgrest
    import io.github.jan.supabase.realtime.Realtime
    import io.github.jan.supabase.storage.Storage

    object SupabaseClient {

        private const val SUPABASE_URL = "https://excpqnqefyvesrpapodu.supabase.co"
        private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImV4Y3BxbnFlZnl2ZXNycGFwb2R1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzI5Nzg4NTcsImV4cCI6MjA4ODU1NDg1N30.CM76pMZv7I-2XfcVcgdjDowU9RvsdBQWZXUDevq8h_I"

        val client = createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
            install(Realtime)

        }

    }