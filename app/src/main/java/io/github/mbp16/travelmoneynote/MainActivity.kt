package io.github.mbp16.travelmoneynote

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.InstallStateUpdatedListener
import io.github.mbp16.travelmoneynote.ui.screens.ExpenseScreen
import io.github.mbp16.travelmoneynote.ui.screens.HomeScreen
import io.github.mbp16.travelmoneynote.ui.screens.SettingsScreen
import io.github.mbp16.travelmoneynote.ui.screens.PersonDetailScreen
import io.github.mbp16.travelmoneynote.ui.theme.TravelMoneyNoteTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var updateLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var snackbarHostState: SnackbarHostState
    
    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            // 다운로드 완료 시 사용자에게 재시작 안내
            showUpdateSnackbar()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.registerListener(installStateUpdatedListener)
        
        updateLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode != RESULT_OK) {
                // 업데이트 취소 또는 실패 처리
            }
        }
        
        checkForAppUpdate()
        
        enableEdgeToEdge()
        setContent {
            snackbarHostState = remember { SnackbarHostState() }
            
            TravelMoneyNoteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TravelMoneyNoteApp(snackbarHostState)
                }
            }
        }
    }
    
    private fun checkForAppUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                )
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 다운로드 완료된 업데이트가 있는지 확인
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                showUpdateSnackbar()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        appUpdateManager.unregisterListener(installStateUpdatedListener)
    }
    
    private fun showUpdateSnackbar() {
        if (!::snackbarHostState.isInitialized) {
            // snackbarHostState가 초기화되지 않은 경우 (setContent 호출 전)
            return
        }
        
        lifecycleScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = getString(R.string.update_available_message),
                actionLabel = getString(R.string.update_restart_button),
                withDismissAction = true
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                appUpdateManager.completeUpdate()
            }
        }
    }
}

@Composable
fun TravelMoneyNoteApp(snackbarHostState: SnackbarHostState) {
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