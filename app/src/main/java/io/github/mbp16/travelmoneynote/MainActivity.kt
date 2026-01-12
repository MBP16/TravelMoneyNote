package io.github.mbp16.travelmoneynote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.mbp16.travelmoneynote.ui.screens.AddCashScreen
import io.github.mbp16.travelmoneynote.ui.screens.AddExpenseScreen
import io.github.mbp16.travelmoneynote.ui.screens.AddPersonScreen
import io.github.mbp16.travelmoneynote.ui.screens.HomeScreen
import io.github.mbp16.travelmoneynote.ui.screens.SettingsScreen
import io.github.mbp16.travelmoneynote.ui.screens.PersonDetailScreen
import io.github.mbp16.travelmoneynote.ui.screens.EditExpenseScreen
import io.github.mbp16.travelmoneynote.ui.theme.TravelMoneyNoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TravelMoneyNoteTheme {
                TravelMoneyNoteApp()
            }
        }
    }
}

@Composable
fun TravelMoneyNoteApp() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToAddPerson = { navController.navigate("add_person") },
                onNavigateToAddCash = { navController.navigate("add_cash") },
                onNavigateToAddExpense = { navController.navigate("add_expense") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToPersonDetail = { personId -> navController.navigate("person_detail/$personId") },
                onNavigateToEditExpense = { expenseId -> navController.navigate("edit_expense/$expenseId") }
            )
        }
        composable("add_person") {
            AddPersonScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("add_cash") {
            AddCashScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("add_expense") {
            AddExpenseScreen(
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
            EditExpenseScreen(
                viewModel = viewModel,
                expenseId = expenseId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}