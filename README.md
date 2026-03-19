# Resenha 💬

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-1.6.1+-blue.svg)](https://developer.android.com/jetpack/compose)
[![MinSDK](https://img.shields.io/badge/MinSDK-24-brightgreen.svg)](https://developer.android.com/about/versions/nougat)
[![TargetSDK](https://img.shields.io/badge/TargetSDK-34-brightgreen.svg)](https://developer.android.com/about/versions/14)

> **Resenha** é um aplicativo de mensagens em Kotlin com Jetpack Compose, oferecendo conversas
> privadas e em grupo com suporte a anexos (imagem, vídeo, PDF, áudio), compartilhamento de
> localização, **notificações locais** e atualização em tempo real via Supabase.

---

## 📋 Sumário

- [Sobre o Projeto](#-sobre-o-projeto)
- [Funcionalidades](#-funcionalidades)
- [Tecnologias Utilizadas](#-tecnologias-utilizadas)
- [Arquitetura e Estrutura](#-arquitetura-e-estrutura)
- [Configuração do Projeto](#-configuração-do-projeto)
- [Permissões do App](#-permissões-do-app)
- [Backend (Supabase)](#-backend-supabase)
- [Autores](#-autores)

---

## 🎯 Sobre o Projeto

O **Resenha** foi desenvolvido para oferecer uma experiência de chat moderna (estilo mensageiro),
com:

- **Login e cadastro**
- **Conversas privadas**
- **Grupos**
- **Mensagens com status** (enviada/recebida/lida)
- **Anexos e mídia**
- **Atualização em tempo real**
- **Criptografia do conteúdo textual** das mensagens

O app utiliza **Supabase** como backend (Auth + Database via PostgREST + Storage + Realtime).

---

## ✨ Funcionalidades

Os itens abaixo descrevem as funcionalidades implementadas e onde aparecem no app.

### 1) Autenticação

- Cadastro e login de usuário (Supabase Auth) **via e-mail/senha**
- Persistência de sessão (ao abrir o app, se estiver logado vai direto para Home)
- Recuperação de senha (envio de e-mail pelo Supabase Auth)

### 2) Tela inicial (Resenhas Ativas / Conversas)

- Lista de conversas privadas e de grupo
- Busca local por conversas
- Fixar conversa (pin) com toque longo
- Badge de mensagens não lidas
- Atualizações periódicas e em tempo real (Supabase Realtime)

### 3) Criar conversa privada

- Buscar pessoas por nome
- Ao selecionar um usuário:
    - abre conversa existente **ou**
    - cria uma nova conversa e navega para o chat

### 4) Conversa (Chat)

- Envio e recebimento de mensagens de texto
- Busca dentro da conversa (filtro por palavra‑chave)
- Status de mensagem (enviada, recebida, lida)
- Agrupamento por data ("Hoje", "Ontem", etc.)
- Exibição de avatar e nome do remetente em grupos
- Excluir conversa (apaga mensagens e remove a conversa no Supabase; mídias enviadas por você também
  são removidas do Storage)

### 5) Anexos e uso de sensores (mídia + GPS/câmera/microfone)

- Envio de mídia/arquivos:
    - Imagem (câmera/galeria)
    - PDF
    - Áudio (upload) + mensagem de voz (gravação no app)
    - Vídeo
- Envio de localização atual (link do Google Maps)
- Upload de arquivos no Supabase Storage
- Visualização/abertura de anexos no chat:
    - Imagem: pré-visualização no chat
    - PDF/Vídeo/Localização: abre via link
    - Áudio: player no chat
- Limite de upload no app: ~15 MB por arquivo

### 6) Grupos

- Criação de grupo
- Gerenciamento do grupo: alterar nome e imagem, adicionar/remover participantes

### 7) Perfil do usuário

- Editar nome, foto e senha

### 8) Segurança

- Criptografia do conteúdo textual das mensagens (AES)

#### O que é AES (Advanced Encryption Standard)

O **AES** é um padrão de criptografia simétrica amplamente utilizado para proteger dados.
Ele trabalha cifrando blocos de informação e pode operar com chaves de **128, 192 ou 256 bits**.
Por ser um padrão consolidado e eficiente, é comum em aplicações que precisam cifrar dados em
repouso (armazenados) ou em trânsito.

#### Como a criptografia foi implementada no app

A criptografia está centralizada no arquivo `app/src/main/java/com/example/resenha/CryptoUtils.kt`.

- O app usa a API de criptografia do Java (`javax.crypto.Cipher`) com o algoritmo **AES**.
- A chave é uma chave **simétrica** (a mesma chave cifra e decifra), definida no código como uma
  string de **16 caracteres** (`ResenhaUfuSecret`), o que corresponde a **128 bits**.
- O texto cifrado é convertido para **Base64** para poder ser armazenado como string no banco (
  Supabase/Postgres) e trafegado normalmente.

**Fluxo no código (resumo):**

1. **Ao enviar mensagens (texto e também o campo `last_message` da conversa):**
    - o conteúdo é cifrado com `CryptoUtils.encrypt(...)` antes do `insert/update` nas tabelas
      `messages` e `conversations`.

2. **Ao carregar mensagens e previews:**
    - o conteúdo vindo do banco é decifrado com `CryptoUtils.decrypt(...)` antes de ser exibido na
      UI.
    - exemplo: na tela de chat o app faz `decrypt` ao montar a lista de mensagens; na Home o preview
      do `last_message` também é decifrado.

3. **Compatibilidade/robustez:**
    - `decrypt()` usa `try/catch` e, se falhar (ex.: mensagens antigas sem criptografia), retorna o
      texto original para evitar quebrar a tela.

### 9) Notificações

- Notificação local quando novas mensagens são detectadas na Home (Android 13+ pede permissão)
- O projeto contém dependência do Firebase Cloud Messaging (FCM), porém push notifications ainda não
  estão completas (não há Service/handlers de recebimento implementados no código atual)

### 10) Logout

- Encerramento de sessão (signOut)

---

## 🛠️ Tecnologias Utilizadas

### Frontend (Android)

- **Kotlin**
- **Jetpack Compose**
- Material Design 3
- Coil (imagens)
- ViewModel Compose (Lifecycle)

### Backend / Cloud

- **Supabase**
    - Auth
    - PostgREST (acesso ao banco)
    - Storage (uploads)
    - Realtime (atualização em tempo real)
- Firebase Cloud Messaging (push)
- Ktor (client Android + websockets/OkHttp engine)
- Kotlinx Serialization (JSON)

### Extras

- Notificações (NotificationCompat)
- Localização (Google Play Services Location)
- FileProvider (para câmera e compartilhamento de arquivos)

---

## 🏗️ Arquitetura e Estrutura

O app está organizado de forma simples (Compose + telas + modelos + client de rede).

### Estrutura (visão geral)

```
Resenha/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/example/resenha/
│           ├── MainActivity.kt
│           ├── HomeScreen.kt
│           ├── ChatScreen.kt
│           ├── SearchUsersScreen.kt
│           ├── CryptoUtils.kt
│           ├── NotificationHelper.kt
│           ├── Conversation.kt
│           ├── data/
│           │   └── Models.kt
│           ├── network/
│           │   └── SupabaseClient.kt
│           └── ui/
│               └── ...
└── build.gradle.kts / settings.gradle.kts
```

### Fluxo de telas (alto nível)

- **Login** → **Home**
- Home:
    - abre conversa → **Chat**
    - buscar pessoas → **SearchUsers**
    - criar grupo → **CreateGroup**
    - perfil → **Profile**

---

## ⚙️ Configuração do Projeto

### Pré-requisitos

- Android Studio (recomendado: versão atual)
- JDK 17
- Dispositivo ou emulador Android (API 24+)

### Como rodar

1. Clone o repositório
2. Abra no Android Studio
3. Sincronize o Gradle
4. Rode o módulo `app`

---

## 🔐 Permissões do App

O app solicita permissões para recursos específicos:

- `INTERNET` (Supabase e rede)
- `POST_NOTIFICATIONS` (notificações)
- `RECORD_AUDIO` (mensagem de voz)
- `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION` (enviar localização)
- `CAMERA` (tirar foto para enviar no chat)

---

## 🗄️ Backend (Supabase)

### Serviços usados

- **Auth**: login/cadastro
- **PostgREST**: acesso às tabelas (ex.: `users`, `conversations`, `messages`,
  `conversation_participants`)
- **Storage**: bucket (ex.: `resenha`) para uploads
- **Realtime**: atualizações de tabelas (conversas/mensagens)

### Dificuldades encontradas

1. Durante o desenvolvimento, a principal dificuldade foi em relação ao **Storage**. Inicialmente a
   ideia era usar **Firebase Storage**, mas ele passou a exigir plano pago, o que inviabilizou até
   testes pequenos como os deste trabalho.
   Por isso, adaptamos parte da estrutura para utilizar o **Supabase** (PostgreSQL + Storage). O
   Supabase também possui limitações no plano gratuito (ex.: cerca de **50 MB por projeto**), porém
   foi suficiente para os testes e para validar o fluxo de anexos (upload e exibição no chat).

2. Dificuldade em **iniciar o projeto** devido ao volume de funcionalidades requeridas.
   Para entender quais abordagens seriam mais viáveis, foi necessário pesquisar/estudar referências
   e até chegar em uma arquitetura e fluxo que fizesse sentido para o escopo do trabalho.

3. Também tivemos desafios ao **unir branches e resolver conflitos de merge** (principalmente em
   arquivos grandes). A resolução exigiu revisar funcionalidades, padronizar trechos de código e
   realizar testes manuais para garantir que o app continuasse funcionando após a integração.

---

## 👨‍💻 Autores

1. [Bruna Teodoro](https://github.com/BTeo08)
2. [Felipe Sérgio](https://github.com/lipesdf)
3. [Ricardo Ranzatti](https://github.com/Ranzatti)
4. [Tainá Peixoto](https://github.com/peixotots)