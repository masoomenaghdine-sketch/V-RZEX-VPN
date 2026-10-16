package com.v2ray.ang.ui.main

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.QRCodeDialog
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
    onPsiphonQuickSetup: () -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val isRunning = uiState.isRunning
    val displayText = mainViewModel.formatStatus(uiState.status)
    val selectedGuid = uiState.selectedGuid
    val doubleColumnDisplay = uiState.doubleColumnDisplay
    val confirmRemove = uiState.confirmRemove
    val shareQRCodeBitmap = uiState.shareQRCodeBitmap

    // PattNG: a move of a profile the storage refused is told once, each refusal by its number, so that one set again
    // right after the last was told is told too; the groups are shown anew, as stored.
    val context = LocalContext.current
    val failureText = stringResource(R.string.toast_failure)
    val moveRefusal = uiState.moveRefusal
    LaunchedEffect(moveRefusal) {
        if (moveRefusal != null) {
            context.toastError(failureText)
            onAction(MainAction.MoveRefusalShown(moveRefusal))
        }
    }

    val isDarkTheme = LocalDarkTheme.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDelAllConfirm by remember { mutableStateOf(false) }
    var showDelDuplicateConfirm by remember { mutableStateOf(false) }
    var showDelInvalidConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by rememberSaveable(stateSaver = ServerDeleteTarget.Saver) {
        mutableStateOf<ServerDeleteTarget?>(null)
    }

    var shareTarget by remember { mutableStateOf<Triple<String, ProfileItem, Boolean>?>(null) }
    val removeServer: (String, String) -> Unit = { guid, profileName ->
        if (confirmRemove) {
            showRemoveConfirm = ServerDeleteTarget(guid, profileName)
        } else {
            onAction(MainAction.RemoveServer(guid))
        }
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { groups.size.coerceAtLeast(1) }
    )

    val lazyListStates = remember { mutableStateMapOf<String, LazyListState>() }
    val lazyGridStates = remember { mutableStateMapOf<String, LazyGridState>() }

    LaunchedEffect(groups) {
        val validGroupIds = groups.map { it.id }.toSet()
        lazyListStates.keys.retainAll(validGroupIds)
        lazyGridStates.keys.retainAll(validGroupIds)
    }

    LaunchedEffect(groups, uiState.selectedGroupId) {
        if (groups.isEmpty()) return@LaunchedEffect
        val selectedIndex = groups.indexOfFirst { it.id == uiState.selectedGroupId }
            .takeIf { it >= 0 } ?: 0
        if (!pagerState.isScrollInProgress && pagerState.settledPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex)
        }
    }

    val latestGroups by rememberUpdatedState(groups)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val currentGroups = latestGroups
                if (page in currentGroups.indices) {
                    onAction(MainAction.SelectGroup(currentGroups[page].id))
                }
            }
    }

    MainDialogs(
        showDelAllConfirm = showDelAllConfirm,
        onDismissDelAll = { showDelAllConfirm = false },
        onConfirmDelAll = { showDelAllConfirm = false; onAction(MainAction.RemoveAllServers) },
        showDelDuplicateConfirm = showDelDuplicateConfirm,
        onDismissDelDuplicate = { showDelDuplicateConfirm = false },
        onConfirmDelDuplicate = { showDelDuplicateConfirm = false; onAction(MainAction.RemoveDuplicateServers) },
        showDelInvalidConfirm = showDelInvalidConfirm,
        onDismissDelInvalid = { showDelInvalidConfirm = false },
        onConfirmDelInvalid = { showDelInvalidConfirm = false; onAction(MainAction.RemoveInvalidServers) },
        showRemoveConfirm = showRemoveConfirm,
        onDismissRemove = { showRemoveConfirm = null },
        onConfirmRemove = { guid -> showRemoveConfirm = null; onAction(MainAction.RemoveServer(guid)) }
    )

    if (shareTarget != null) {
        val (guid, profile, more) = shareTarget!!
        ShareMethodDialog(
            guid = guid,
            profile = profile,
            more = more,
            onDismiss = { shareTarget = null },
            onAction = onAction,
            onRemove = removeServer,
        )
    }
    if (shareQRCodeBitmap != null) {
        QRCodeDialog(bitmap = shareQRCodeBitmap, onDismiss = { onAction(MainAction.DismissQRCodeDialog) })
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainDrawerContent(
                drawerState = drawerState,
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    onNavigate(route)
                }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            topBar = {
                MainTopBar(
                    isLoading = isLoading,
                    showSearch = showSearch,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { query: String ->
                        searchQuery = query
                        onAction(MainAction.Search(query))
                    },
                    onSearchClose = {
                        searchQuery = ""
                        onAction(MainAction.Search(""))
                        showSearch = false
                    },
                    onSearchToggle = { show: Boolean -> showSearch = show },
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onAction = onAction,
                    onMoreMenuAction = { action ->
                        when (action) {
                            MainMoreMenuAction.TorNetwork -> onNavigate(MainDestination.TorNetwork)
                            MainMoreMenuAction.PsiphonCdn -> onPsiphonQuickSetup()
                            MainMoreMenuAction.RestartService -> onAction(MainAction.RestartService)
                            MainMoreMenuAction.DeleteAll -> showDelAllConfirm = true
                            MainMoreMenuAction.DeleteDuplicate -> showDelDuplicateConfirm = true
                            MainMoreMenuAction.DeleteInvalid -> showDelInvalidConfirm = true
                            MainMoreMenuAction.ExportAll -> onAction(MainAction.ExportAll)
                            MainMoreMenuAction.ExportVZV -> onAction(MainAction.ExportVZV)
                            MainMoreMenuAction.ShareVZV -> onAction(MainAction.ShareVZV)
                            MainMoreMenuAction.LocateSelected -> onAction(MainAction.LocateSelectedServer)
                            MainMoreMenuAction.SortByTestResults -> onAction(MainAction.SortByTestResults)
                            MainMoreMenuAction.TestAll -> onAction(MainAction.TestAllServers)
                            MainMoreMenuAction.TestAllRealPing -> onAction(MainAction.TestRealAllServers)
                            MainMoreMenuAction.UpdateSubscriptions -> onAction(MainAction.UpdateSubscriptions)
                        }
                    }
                )
            },
            bottomBar = {
                MainBottomBar(
                    displayText = displayText,
                    isRunning = isRunning,
                    isDarkTheme = isDarkTheme,
                    onAction = onAction
                )
            },
            floatingActionButton = {},
        ) { innerPadding ->
            val layoutDirection = LocalLayoutDirection.current

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                ConnectionStatusHero(
                    isRunning = isRunning,
                    displayText = displayText,
                    onToggle = { onAction(MainAction.ToggleService) }
                )
                QuickAccessPanel(
                    onAiCon = { onAction(MainAction.GenerateAiConfig) },
                    onAiShield = { onNavigate(MainDestination.AiShield) },
                    onCnr = { onNavigate(MainDestination.Cnr) },
                    onCountries = { onNavigate(MainDestination.Countries) },
                    onDevice = { onNavigate(MainDestination.Device) },
                    onTor = { onNavigate(MainDestination.TorNetwork) },
                    onSettings = { onNavigate(MainDestination.Settings) }
                )
                if (groups.isNotEmpty()) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (groups.size > 1) {
                            GroupTabBar(
                                groups = groups,
                                selectedTabIndex = pagerState.currentPage.coerceIn(0, groups.lastIndex),
                                mainViewModel = mainViewModel,
                                onTabClick = { targetIndex ->
                                    scope.launch {
                                        pagerState.navigateToPageOptimized(
                                            targetPage = targetIndex,
                                            animateAdjacentPage = true
                                        )
                                    }
                                }
                            )
                        }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = true,
                            beyondViewportPageCount = 1,
                            key = { page -> groups.getOrNull(page)?.id ?: "group-page-$page" }
                        ) { page ->
                            val group = groups.getOrNull(page) ?: return@HorizontalPager

                            GroupPagerPage(
                                groupId = group.id,
                                mainViewModel = mainViewModel,
                                selectedGuid = selectedGuid,
                                locateTarget = uiState.locateTarget,
                                doubleColumnDisplay = doubleColumnDisplay,
                                searchQuery = searchQuery,
                                lazyListStates = lazyListStates,
                                lazyGridStates = lazyGridStates,
                                onSelectServer = { guid -> onAction(MainAction.SelectServer(guid)) },
                                onEditServer = { guid, profile -> onAction(MainAction.EditServer(guid, profile)) },
                                onShareServer = { guid, profile ->
                                    shareTarget = Triple(guid, profile, false)
                                },
                                onMoreServer = { guid, profile ->
                                    shareTarget = Triple(guid, profile, true)
                                },
                                onRemoveServer = removeServer,
                                contentPadding = PaddingValues(
                                    start = 0.dp,
                                    top = 0.dp,
                                    end = 0.dp,
                                    bottom = 80.dp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ConnectionStatusHero(
    isRunning: Boolean,
    displayText: String,
    onToggle: () -> Unit
) {
    val cyan = Color(0xFF43F5D0)
    val violet = Color(0xFF9B7BFF)
    val accent = if (isRunning) cyan else violet
    val glow = rememberInfiniteTransition(label = "vorzexConnectionGlow")
    val glowAlpha by glow.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vorzexGlowAlpha"
    )
    val shape = RoundedCornerShape(28.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .border(1.dp, accent.copy(alpha = glowAlpha), shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = if (isRunning) {
                            listOf(Color(0xFF092E35), Color(0xFF122C40), Color(0xFF211A43))
                        } else {
                            listOf(Color(0xFF17172F), Color(0xFF222044), Color(0xFF301B48))
                        }
                    ),
                    shape = shape
                )
                .padding(horizontal = 18.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(accent, CircleShape)
                    )
                    Text(
                        text = if (isRunning) "CONNECTED · اتصال برقرار" else "SYSTEM READY · آمادهٔ اتصال",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                }
                Text(
                    text = "VØRZEX VPN",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Text(
                    text = "Freedom Beyond Borders",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFB9A8FF)
                )
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD7D9F2),
                    maxLines = 2
                )
                Text(
                    text = if (isRunning) "برای قطع اتصال لمس کن" else "اتصال هوشمند · حریم خصوصی · آزادی",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB8B8D8)
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.vorzex_mascot),
                    contentDescription = "VØRZEX AI mascot",
                    modifier = Modifier.size(66.dp),
                    contentScale = ContentScale.Fit
                )
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(accent.copy(alpha = 0.12f), CircleShape)
                        .border(1.5.dp, accent.copy(alpha = 0.72f), CircleShape)
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isRunning) "Ⅱ" else "ϟ",
                        style = MaterialTheme.typography.headlineLarge,
                        color = accent
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAccessPanel(
    onAiCon: () -> Unit,
    onAiShield: () -> Unit,
    onCnr: () -> Unit,
    onCountries: () -> Unit,
    onDevice: () -> Unit,
    onTor: () -> Unit,
    onSettings: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 3.dp, bottom = 9.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .border(1.dp, Color(0xFF8F78FF).copy(alpha = 0.52f), shape)
                .clickable(onClick = onAiCon),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF25204A), Color(0xFF1A3450), Color(0xFF143A43))
                        ),
                        shape
                    )
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFF9B7BFF).copy(alpha = 0.16f), RoundedCornerShape(15.dp))
                        .border(1.dp, Color(0xFF9B7BFF).copy(alpha = 0.45f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.vorzex_mascot),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("AI CON", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(
                        "ساخت پروفایل، آزمایش اتصال و انتخاب خودکار",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD5D8F0)
                    )
                }
                Text("شروع ←", style = MaterialTheme.typography.labelLarge, color = Color(0xFF73F2DE))
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item(key = "ai-net") {
                QuickNavItem(emoji = "◈", title = "AI NET", onClick = onAiShield)
            }
            item(key = "cnr") {
                QuickNavItem(emoji = "🌐", title = "کانفیگ‌های CNR", onClick = onCnr)
            }
            item(key = "countries") {
                QuickNavItem(emoji = "🌍", title = "کشورها", onClick = onCountries)
            }
            item(key = "tor") {
                QuickNavItem(emoji = "🧅", title = "شبکه Tor", onClick = onTor)
            }
            item(key = "device") {
                QuickNavItem(emoji = "📱", title = "دستگاه", onClick = onDevice)
            }
            item(key = "settings") {
                QuickNavItem(emoji = "⚙️", title = "تنظیمات", onClick = onSettings)
            }
        }
    }
}

@Composable
private fun QuickNavItem(
    emoji: String,
    title: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val vorzexCyan = Color(0xFF36E3D2)
    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                shape = shape
            )
            .border(
                width = 1.dp,
                color = vorzexCyan.copy(alpha = 0.24f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = emoji, style = MaterialTheme.typography.titleLarge)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
