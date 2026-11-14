package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repository.LiftContentRepository
import com.example.data.repository.UserPreferencesRepository
import com.example.data.repository.WatchPartyRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOut
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.LiftBrowserScreen
import com.example.ui.screens.PartiesScreen
import com.example.ui.screens.WatchRoomScreen
import com.example.ui.screens.WatchlistScreen
import com.example.ui.theme.LiftPartyTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.WatchRoomViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_DISCOVER = "discover"
const val ROUTE_PARTIES = "parties"
const val ROUTE_WATCHLIST = "watchlist"
const val ROUTE_LIFT_BROWSER = "lift_browser"
const val ROUTE_WATCH_ROOM = "watch_room"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiftPartyTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            LiftPartyAppShell(currentUser = user)
        }
    }
}

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiftPartyAppShell(currentUser: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val displayName = currentUser.displayName?.takeIf { it.isNotBlank() }
        ?: currentUser.email?.substringBefore('@')
        ?: "Viewer"

    val mainViewModel: MainViewModel = viewModel(
        key = currentUser.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MainViewModel(
                    watchPartyRepository = WatchPartyRepository(db),
                    liftContentRepository = LiftContentRepository(),
                    preferencesRepository = UserPreferencesRepository(app),
                    currentUserId = currentUser.uid,
                    currentUserDisplayName = displayName,
                    currentUserPhotoUrl = currentUser.photoUrl?.toString(),
                )
            }
        },
    )

    val liftSourceUrl by mainViewModel.liftSourceUrl.collectAsStateWithLifecycle()
    val liftFetchState by mainViewModel.liftFetchState.collectAsStateWithLifecycle()
    val savedMediaState by mainViewModel.savedMediaState.collectAsStateWithLifecycle()
    val publicRoomsState by mainViewModel.publicRoomsState.collectAsStateWithLifecycle()
    val myRoomsState by mainViewModel.myRoomsState.collectAsStateWithLifecycle()
    val statusBanner by mainViewModel.statusBanner.collectAsStateWithLifecycle()
    val isBusy by mainViewModel.isBusy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            mainViewModel.clearStatusBanner()
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_DISCOVER

    val topDestinations = remember {
        listOf(
            TopLevelDestination(
                route = ROUTE_DISCOVER,
                labelRes = R.string.nav_discover,
                selectedIcon = Icons.Filled.Explore,
                unselectedIcon = Icons.Outlined.Explore,
                testTag = "nav_discover",
            ),
            TopLevelDestination(
                route = ROUTE_PARTIES,
                labelRes = R.string.nav_parties,
                selectedIcon = Icons.Filled.LiveTv,
                unselectedIcon = Icons.Outlined.LiveTv,
                testTag = "nav_parties",
            ),
            TopLevelDestination(
                route = ROUTE_WATCHLIST,
                labelRes = R.string.nav_watchlist,
                selectedIcon = Icons.Filled.Bookmark,
                unselectedIcon = Icons.Outlined.BookmarkBorder,
                testTag = "nav_watchlist",
            ),
        )
    }

    val showShellChrome = currentRoute in setOf(ROUTE_DISCOVER, ROUTE_PARTIES, ROUTE_WATCHLIST)

    fun navigateToRoom(roomId: String) {
        navController.navigate("$ROUTE_WATCH_ROOM/${Uri.encode(roomId)}") {
            launchSingleTop = true
        }
    }

    fun navigateToBrowser(url: String) {
        navController.navigate("$ROUTE_LIFT_BROWSER?url=${Uri.encode(url)}") {
            launchSingleTop = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (showShellChrome) {
                    val titleText = when (currentRoute) {
                        ROUTE_PARTIES -> stringResource(R.string.parties_title)
                        ROUTE_WATCHLIST -> stringResource(R.string.watchlist_title)
                        else -> stringResource(R.string.app_name)
                    }
                    TopAppBar(
                        title = {
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            },
            bottomBar = {
                if (showShellChrome && !useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        topDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(ROUTE_DISCOVER) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes),
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (showShellChrome && useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        topDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(ROUTE_DISCOVER) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes),
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_DISCOVER,
                    ) {
                        composable(ROUTE_DISCOVER) {
                            val savedList = (savedMediaState as? UiState.Success)?.data.orEmpty()
                            DiscoverScreen(
                                liftSourceUrl = liftSourceUrl,
                                liftFetchState = liftFetchState,
                                savedMedia = savedList,
                                isBusy = isBusy,
                                onUpdateLiftSourceAndFetch = { url ->
                                    mainViewModel.updateLiftSourceUrl(url, fetchImmediately = true)
                                },
                                onOpenLiftBrowser = { url -> navigateToBrowser(url) },
                                onWatchTogetherItem = { item ->
                                    mainViewModel.resolveItemAndCreateParty(
                                        item = item,
                                        isPublic = true,
                                        onRoomCreated = { roomId -> navigateToRoom(roomId) },
                                        onNeedsBrowserExtraction = { pageUrl -> navigateToBrowser(pageUrl) },
                                    )
                                },
                                onToggleSaveItem = { item ->
                                    val existing = savedList.firstOrNull { it.sourcePageUrl == item.sourcePageUrl }
                                    if (existing != null) {
                                        mainViewModel.removeSavedMedia(existing.id)
                                    } else {
                                        mainViewModel.saveMedia(item)
                                    }
                                },
                                onCreatePartyFromUrls = { title, pageUrl, streamUrl, isPublic ->
                                    mainViewModel.createPartyFromManualOrBrowserUrl(
                                        title = title,
                                        sourcePageUrl = pageUrl,
                                        streamUrl = streamUrl,
                                        isPublic = isPublic,
                                        onRoomCreated = { roomId -> navigateToRoom(roomId) },
                                    )
                                },
                            )
                        }

                        composable(ROUTE_PARTIES) {
                            PartiesScreen(
                                currentUserId = currentUser.uid,
                                publicRoomsState = publicRoomsState,
                                myRoomsState = myRoomsState,
                                onJoinRoom = { roomId ->
                                    mainViewModel.joinRoom(roomId) { joinedId ->
                                        navigateToRoom(joinedId)
                                    }
                                },
                                onJoinByCode = { code ->
                                    mainViewModel.joinRoomByCode(code) { joinedId ->
                                        navigateToRoom(joinedId)
                                    }
                                },
                                onNavigateToDiscover = {
                                    navController.navigate(ROUTE_DISCOVER) {
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }

                        composable(ROUTE_WATCHLIST) {
                            WatchlistScreen(
                                displayName = displayName,
                                email = currentUser.email,
                                photoUrl = currentUser.photoUrl?.toString(),
                                liftSourceUrl = liftSourceUrl,
                                savedMediaState = savedMediaState,
                                onWatchSavedItem = { item ->
                                    mainViewModel.resolveItemAndCreateParty(
                                        item = item,
                                        isPublic = true,
                                        onRoomCreated = { roomId -> navigateToRoom(roomId) },
                                        onNeedsBrowserExtraction = { pageUrl -> navigateToBrowser(pageUrl) },
                                    )
                                },
                                onRemoveSavedItem = { mediaId ->
                                    mainViewModel.removeSavedMedia(mediaId)
                                },
                                onSignOut = {
                                    signOut(credentialManager = credentialManager, scope = scope)
                                },
                            )
                        }

                        composable(
                            route = "$ROUTE_LIFT_BROWSER?url={url}",
                            arguments = listOf(navArgument("url") { type = NavType.StringType; defaultValue = "" }),
                        ) { entry ->
                            val targetUrl = entry.arguments?.getString("url").orEmpty().ifBlank { liftSourceUrl }
                            LiftBrowserScreen(
                                initialUrl = targetUrl,
                                onBack = { navController.popBackStack() },
                                onStartPartyWithStream = { title, pageUrl, streamUrl ->
                                    mainViewModel.createPartyFromManualOrBrowserUrl(
                                        title = title,
                                        sourcePageUrl = pageUrl,
                                        streamUrl = streamUrl,
                                        isPublic = true,
                                        onRoomCreated = { roomId ->
                                            navController.popBackStack()
                                            navigateToRoom(roomId)
                                        },
                                    )
                                },
                            )
                        }

                        composable(
                            route = "$ROUTE_WATCH_ROOM/{roomId}",
                            arguments = listOf(navArgument("roomId") { type = NavType.StringType }),
                        ) { entry ->
                            val roomId = entry.arguments?.getString("roomId").orEmpty()
                            val roomViewModel: WatchRoomViewModel = viewModel(
                                key = "${currentUser.uid}_$roomId",
                                factory = viewModelFactory {
                                    initializer {
                                        val app = checkNotNull(this[APPLICATION_KEY])
                                        val databaseId = app.getString(R.string.firestore_database_id)
                                        val db = FirebaseFirestore.getInstance(databaseId)
                                        WatchRoomViewModel(
                                            repository = WatchPartyRepository(db),
                                            roomId = roomId,
                                            currentUserId = currentUser.uid,
                                            currentUserDisplayName = displayName,
                                        )
                                    }
                                },
                            )

                            WatchRoomScreen(
                                viewModel = roomViewModel,
                                onBack = { navController.popBackStack() },
                                onOpenSourceInBrowser = { pageUrl -> navigateToBrowser(pageUrl) },
                            )
                        }
                    }
                }
            }
        }
    }
}
