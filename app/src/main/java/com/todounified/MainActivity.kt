package com.todounified

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.todounified.ui.screens.MainScreen
import com.todounified.ui.theme.TodoUnifiedTheme
import com.todounified.viewmodel.TodoViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TodoUnifiedTheme {
                val vm: TodoViewModel = viewModel()
                MainScreen(vm)
            }
        }
    }
}
