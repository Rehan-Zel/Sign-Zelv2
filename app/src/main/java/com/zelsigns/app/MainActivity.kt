package com.zelsigns.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zelsigns.app.ui.SignZelApp
import com.zelsigns.app.ui.SignZelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            SignZelTheme(mode = vm.themeMode) {
                SignZelApp(vm)
            }
        }
    }
}
