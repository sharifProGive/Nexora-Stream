/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.zentora.nexora.stream.data.database.entities.PrivateFriendEntity
import com.zentora.nexora.stream.data.repository.FriendsActionResult
import com.zentora.nexora.stream.data.repository.FriendsRepository
import com.zentora.nexora.stream.data.repository.FriendsSearchResult
import com.zentora.nexora.stream.engine.FriendPerson
import com.zentora.nexora.stream.engine.FriendRelation
import com.zentora.nexora.stream.engine.PersonHit
import com.zentora.nexora.stream.ui.theme.NexoraCyanGlow
import com.zentora.nexora.stream.ui.theme.PremiumCard
import com.zentora.nexora.stream.ui.theme.PremiumIconBadge
import com.zentora.nexora.stream.ui.theme.premiumGlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val REFRESH_INTERVAL_MS = 45_000L
private const val REFRESH_JITTER_MS = 15_000L
private const val SEARCH_DEBOUNCE_MS = 400L
private const val MIN_QUERY_LENGTH = 3

private const val TAB_FRIENDS = 0
private const val TAB_REQUESTS = 1
private const val TAB_FIND = 2

/**
 * Friends, backed by the Nexora server. Three tabs:
 *  - Friends:  confirmed friends (opens the chat, call buttons, remove)
 *  - Requests: requests received (accept / decline) and requests sent (cancel)
 *  - Find:     search by handle and send a request
 *
 * While the screen is visible it asks the server for news every 45 to 60 seconds, and again when the screen comes
 * back to the foreground, so a request sent from another phone shows up without any manual step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onOpenChat: (String) -> Unit,
    onStartCall: (name: String, handle: String, isVideo: Boolean) -> Unit
) {
    val context = LocalContext.current
    val friendsRepo = remember { FriendsRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    val state by friendsRepo.state.collectAsState()
    val friends by friendsRepo.friends.collectAsState(initial = emptyList())

    var tab by rememberSaveable { mutableIntStateOf(TAB_FRIENDS) }
    var busy by remember { mutableStateOf(setOf<String>()) }
    var pendingRemoval by remember { mutableStateOf<PrivateFriendEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                friendsRepo.refresh()
                delay(REFRESH_INTERVAL_MS + Random.nextLong(0L, REFRESH_JITTER_MS))
            }
        }
    }

    val announce: (String) -> Unit = { message ->
        scope.launch { snackbar.showSnackbar(message) }
    }

    /** Runs one server action; the same [key] cannot be started twice while it is still running. */
    val runAction: (String, suspend () -> FriendsActionResult) -> Unit = { key, block ->
        if (key !in busy) {
            busy = busy + key
            scope.launch {
                try {
                    when (val result = block()) {
                        is FriendsActionResult.Done -> announce(result.message)
                        is FriendsActionResult.Failed -> announce(result.message)
                    }
                } finally {
                    busy = busy - key
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.premiumGlow(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Friends",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val handle = state.account?.handle.orEmpty()
                        if (handle.isNotBlank()) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NexoraCyanGlow.copy(alpha = 0.14f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = NexoraCyanGlow,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = handle,
                                    color = NexoraCyanGlow,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { scope.launch { friendsRepo.refresh() } },
                        enabled = !state.isRefreshing,
                        modifier = Modifier.testTag("friends_refresh_button")
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = NexoraCyanGlow
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = tab,
                containerColor = Color.Transparent,
                contentColor = NexoraCyanGlow
            ) {
                Tab(
                    selected = tab == TAB_FRIENDS,
                    onClick = { tab = TAB_FRIENDS },
                    text = { Text("Friends", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("friends_tab_friends")
                )
                Tab(
                    selected = tab == TAB_REQUESTS,
                    onClick = { tab = TAB_REQUESTS },
                    text = {
                        BadgedBox(
                            badge = {
                                if (state.incoming.isNotEmpty()) Badge { Text("${state.incoming.size}") }
                            }
                        ) {
                            Text("Requests", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    modifier = Modifier.testTag("friends_tab_requests")
                )
                Tab(
                    selected = tab == TAB_FIND,
                    onClick = { tab = TAB_FIND },
                    text = { Text("Find", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("friends_tab_find")
                )
            }

            val error = state.errorMessage
            if (error != null) {
                PremiumCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { scope.launch { friendsRepo.refresh() } }) {
                            Text("Retry", color = NexoraCyanGlow, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            when (tab) {
                TAB_FRIENDS -> FriendsTab(
                    friends = friends,
                    hasLoaded = state.hasLoaded,
                    hasError = error != null,
                    canModify = state.hasLoaded,
                    onOpenChat = onOpenChat,
                    onStartCall = onStartCall,
                    onRemove = { pendingRemoval = it },
                    onFind = { tab = TAB_FIND }
                )
                TAB_REQUESTS -> RequestsTab(
                    incoming = state.incoming,
                    outgoing = state.outgoing,
                    busy = busy,
                    onAccept = { person -> runAction("respond:${person.nexoraId}") { friendsRepo.respond(person, true) } },
                    onDecline = { person -> runAction("respond:${person.nexoraId}") { friendsRepo.respond(person, false) } },
                    onCancel = { person -> runAction("cancel:${person.nexoraId}") { friendsRepo.cancelRequest(person) } }
                )
                else -> FindTab(
                    myHandle = state.account?.handle.orEmpty(),
                    friendIds = state.friends.map { it.nexoraId }.toSet(),
                    outgoingIds = state.outgoing.map { it.nexoraId }.toSet(),
                    incomingIds = state.incoming.map { it.nexoraId }.toSet(),
                    hasLoaded = state.hasLoaded,
                    busy = busy,
                    search = { query -> friendsRepo.search(query) },
                    onAdd = { person -> runAction("add:${person.nexoraId}") { friendsRepo.sendRequest(person) } },
                    onAccept = { person -> runAction("respond:${person.nexoraId}") { friendsRepo.respond(person, true) } }
                )
            }
        }
    }

    val removal = pendingRemoval
    if (removal != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove ${removal.displayName}?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${removal.handle} will no longer be your friend. You can send a new request later.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRemoval = null
                        runAction("remove:${removal.handle}") { friendsRepo.removeFriend(removal.handle) }
                    }
                ) { Text("Remove", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Friends tab
// ---------------------------------------------------------------------------

@Composable
private fun FriendsTab(
    friends: List<PrivateFriendEntity>,
    hasLoaded: Boolean,
    hasError: Boolean,
    canModify: Boolean,
    onOpenChat: (String) -> Unit,
    onStartCall: (name: String, handle: String, isVideo: Boolean) -> Unit,
    onRemove: (PrivateFriendEntity) -> Unit,
    onFind: () -> Unit
) {
    if (friends.isEmpty()) {
        if (!hasLoaded && !hasError) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NexoraCyanGlow)
            }
        } else {
            EmptyState(
                icon = Icons.Outlined.Group,
                title = "No friends yet",
                message = "Find people by their handle and send them a request. Once they accept, they appear here on every phone you sign in on.",
                actionLabel = "Find friends",
                onAction = onFind
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(friends, key = { it.handle }) { friend ->
            PersonCard(
                name = friend.displayName,
                subtitle = friend.lastMessage.ifBlank { friend.handle },
                avatarUri = friend.avatarUri,
                onClick = { onOpenChat(friend.handle) }
            ) {
                if (friend.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(NexoraCyanGlow),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${friend.unreadCount}",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                IconButton(
                    onClick = { onStartCall(friend.displayName, friend.handle, false) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Voice call", tint = NexoraCyanGlow, modifier = Modifier.size(19.dp))
                }
                IconButton(
                    onClick = { onStartCall(friend.displayName, friend.handle, true) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = "Video call", tint = NexoraCyanGlow, modifier = Modifier.size(21.dp))
                }
                if (canModify) {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Remove friend") },
                                onClick = {
                                    menuOpen = false
                                    onRemove(friend)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Requests tab
// ---------------------------------------------------------------------------

@Composable
private fun RequestsTab(
    incoming: List<FriendPerson>,
    outgoing: List<FriendPerson>,
    busy: Set<String>,
    onAccept: (FriendPerson) -> Unit,
    onDecline: (FriendPerson) -> Unit,
    onCancel: (FriendPerson) -> Unit
) {
    if (incoming.isEmpty() && outgoing.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.MailOutline,
            title = "No open requests",
            message = "Requests you receive and requests you send wait here until they are answered.",
            actionLabel = null,
            onAction = {}
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (incoming.isNotEmpty()) {
            item(key = "header_incoming") { SectionTitle("Received (${incoming.size})") }
            items(incoming, key = { "in_" + it.nexoraId }) { person ->
                val working = "respond:${person.nexoraId}" in busy
                PersonCard(
                    name = person.username.ifBlank { person.handle.removePrefix("@") },
                    subtitle = person.handle,
                    avatarUri = person.avatarUri
                ) {
                    TextButton(onClick = { onDecline(person) }, enabled = !working) {
                        Text("Decline", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { onAccept(person) }, enabled = !working) {
                        Text("Accept", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (outgoing.isNotEmpty()) {
            item(key = "header_outgoing") { SectionTitle("Sent (${outgoing.size})") }
            items(outgoing, key = { "out_" + it.nexoraId }) { person ->
                val working = "cancel:${person.nexoraId}" in busy
                PersonCard(
                    name = person.username.ifBlank { person.handle.removePrefix("@") },
                    subtitle = "${person.handle} · waiting for an answer",
                    avatarUri = person.avatarUri
                ) {
                    OutlinedButton(onClick = { onCancel(person) }, enabled = !working) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Find tab
// ---------------------------------------------------------------------------

@Composable
private fun FindTab(
    myHandle: String,
    friendIds: Set<String>,
    outgoingIds: Set<String>,
    incomingIds: Set<String>,
    hasLoaded: Boolean,
    busy: Set<String>,
    search: suspend (String) -> FriendsSearchResult,
    onAdd: (FriendPerson) -> Unit,
    onAccept: (FriendPerson) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<PersonHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    val clean = query.trim().removePrefix("@")

    LaunchedEffect(clean) {
        if (clean.length < MIN_QUERY_LENGTH) {
            hits = emptyList()
            searchError = null
            searching = false
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        searching = true
        when (val result = search(clean)) {
            is FriendsSearchResult.Hits -> {
                hits = result.people
                searchError = null
            }
            is FriendsSearchResult.Failed -> {
                hits = emptyList()
                searchError = result.message
            }
        }
        searching = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (myHandle.isNotBlank()) {
            item(key = "my_handle") {
                PremiumCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PremiumIconBadge(icon = Icons.Outlined.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Your handle", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = myHandle,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = NexoraCyanGlow,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Friends find you with this handle.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item(key = "search_field") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.filter { c -> c.isLetterOrDigit() || c == '@' || c == '_' || c == '.' } },
                placeholder = { Text("Search by handle, e.g. @alex", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = NexoraCyanGlow)
                    } else if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("friends_search_field")
            )
        }

        val error = searchError
        when {
            error != null -> item(key = "search_error") {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(4.dp))
            }
            clean.length < MIN_QUERY_LENGTH -> item(key = "search_hint") {
                Text(
                    text = "Type at least $MIN_QUERY_LENGTH characters of a handle.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(4.dp)
                )
            }
            hits.isEmpty() && !searching -> item(key = "search_empty") {
                Text(
                    text = "Nobody found for \"$clean\".",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }

        items(hits, key = { it.person.nexoraId }) { hit ->
            val person = hit.person
            // Once the server list is loaded it is the authority; the relation that came with the search result
            // is only used until then.
            val relation = if (hasLoaded) {
                when {
                    person.nexoraId in friendIds -> FriendRelation.FRIENDS
                    person.nexoraId in outgoingIds -> FriendRelation.REQUEST_SENT
                    person.nexoraId in incomingIds -> FriendRelation.REQUEST_RECEIVED
                    else -> FriendRelation.NONE
                }
            } else {
                hit.relation
            }
            PersonCard(
                name = person.username.ifBlank { person.handle.removePrefix("@") },
                subtitle = person.handle,
                avatarUri = person.avatarUri
            ) {
                when (relation) {
                    FriendRelation.NONE -> Button(
                        onClick = { onAdd(person) },
                        enabled = "add:${person.nexoraId}" !in busy
                    ) { Text("Add", fontWeight = FontWeight.Bold) }
                    FriendRelation.REQUEST_SENT -> OutlinedButton(onClick = {}, enabled = false) { Text("Requested") }
                    FriendRelation.REQUEST_RECEIVED -> Button(
                        onClick = { onAccept(person) },
                        enabled = "respond:${person.nexoraId}" !in busy
                    ) { Text("Accept", fontWeight = FontWeight.Bold) }
                    FriendRelation.FRIENDS -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = NexoraCyanGlow, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Friends", color = NexoraCyanGlow, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun PersonCard(
    name: String,
    subtitle: String,
    avatarUri: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit
) {
    PremiumCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PersonAvatar(name = name, avatarUri = avatarUri, size = 48.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            trailing()
        }
    }
}

@Composable
private fun PersonAvatar(name: String, avatarUri: String, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(NexoraCyanGlow.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUri.isNotBlank()) {
            AsyncImage(
                model = avatarUri,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = name.trim().take(1).uppercase(),
                color = NexoraCyanGlow,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PremiumIconBadge(icon = icon, badgeSize = 76.dp, iconSize = 36.dp)
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null) {
                Spacer(modifier = Modifier.height(22.dp))
                Button(onClick = onAction, shape = RoundedCornerShape(20.dp)) {
                    Text(actionLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
