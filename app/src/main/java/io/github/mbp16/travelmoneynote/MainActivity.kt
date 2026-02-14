package io.github.mbp16.travelmoneynote

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.mbp16.travelmoneynote.ui.screens.ExpenseScreen
import io.github.mbp16.travelmoneynote.ui.screens.HomeScreen
import io.github.mbp16.travelmoneynote.ui.screens.PersonDetailScreen
import io.github.mbp16.travelmoneynote.ui.screens.SettingsScreen
import io.github.mbp16.travelmoneynote.ui.theme.TravelMoneyNoteTheme

class MainActivity : ComponentActivity() {
    private lateinit var helper: InAppUpdateHelper
    private val snackbarHostState = SnackbarHostState()

    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> helper.onActivityResult(result.resultCode) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        helper = InAppUpdateHelper(
            activity = this,
            launcher = updateLauncher,
            priorityThreshold = 4,
            snackbarHostState = snackbarHostState,
            scope = lifecycleScope
        )
        helper.check()

        enableEdgeToEdge()
        setContent {
            TravelMoneyNoteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        TravelMoneyNoteApp()
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 20.dp)
                        ) {
                            Snackbar(
                                snackbarData = it,
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        helper.resumeCheck()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        helper.onDestroy()
    }
}

@Composable
fun TravelMoneyNoteApp() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()

    // 카메라 권한 요청 로직 추가
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            // 권한 허용/거부 시 처리 로직을 여기에 작성할 수 있습니다.
        }
    )

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300)
            )
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(300)
            )
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(300)
            )
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300)
            )
        }
    ) {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToAddExpense = { navController.navigate("add_expense") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToPersonDetail = { personId -> navController.navigate("person_detail/$personId") },
                onNavigateToEditExpense = { expenseId -> navController.navigate("edit_expense/$expenseId") }
            )
        }
        composable("add_expense") {
            ExpenseScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("person_detail/{personId}") { backStackEntry ->
            val personId = backStackEntry.arguments?.getString("personId")?.toLongOrNull() ?: 0L
            PersonDetailScreen(
                viewModel = viewModel,
                personId = personId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("edit_expense/{expenseId}") { backStackEntry ->
            val expenseId = backStackEntry.arguments?.getString("expenseId")?.toLongOrNull() ?: 0L
            ExpenseScreen(
                viewModel = viewModel,
                expenseId = expenseId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}