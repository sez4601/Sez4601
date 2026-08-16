package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.AddEditProductScreen
import com.example.ui.screens.ProductListScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ProductViewModel

class MainActivity : ComponentActivity() {
    private val productViewModel: ProductViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(viewModel = productViewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: ProductViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "product_list"
    ) {
        composable("product_list") {
            ProductListScreen(
                viewModel = viewModel,
                onNavigateToAddProduct = {
                    navController.navigate("add_product")
                },
                onNavigateToEditProduct = { productId ->
                    navController.navigate("edit_product/$productId")
                }
            )
        }

        composable("add_product") {
            AddEditProductScreen(
                productId = 0L,
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "edit_product/{productId}",
            arguments = listOf(
                navArgument("productId") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
            AddEditProductScreen(
                productId = productId,
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
