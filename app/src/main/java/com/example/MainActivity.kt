package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.ProductEntity
import com.example.ui.screens.AddEditProductScreen
import com.example.ui.screens.ProductListScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ProductViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProductInventoryApp()
                }
            }
        }
    }
}

@Composable
fun ProductInventoryApp() {
    val navController = rememberNavController()
    val viewModel: ProductViewModel = viewModel()
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }

    NavHost(
        navController = navController,
        startDestination = "product_list",
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        composable("product_list") {
            ProductListScreen(
                viewModel = viewModel,
                onNavigateToAddProduct = {
                    editingProduct = null
                    navController.navigate("add_edit_product")
                },
                onNavigateToEditProduct = { product ->
                    editingProduct = product
                    navController.navigate("add_edit_product")
                }
            )
        }

        composable("add_edit_product") {
            AddEditProductScreen(
                viewModel = viewModel,
                existingProduct = editingProduct,
                onNavigateBack = {
                    editingProduct = null
                    navController.popBackStack()
                }
            )
        }
    }
}
