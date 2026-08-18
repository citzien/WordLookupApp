package com.school.wordhelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.wordhelper.ui.AppNav
import com.school.wordhelper.ui.WordHelperViewModel
import com.school.wordhelper.ui.theme.WordHelperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WordHelperTheme {
                val viewModel: WordHelperViewModel = viewModel()
                AppNav(viewModel)
            }
        }
    }
}
