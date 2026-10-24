package com.example

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.FloatingBallScreen
import com.example.ui.FloatingBallViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    companion object {
        @Volatile
        var activeInstance: MainActivity? = null
            private set

        fun closeAndRemoveFromRecents() {
            try {
                activeInstance?.finishAndRemoveTask()
                activeInstance = null
            } catch (e: Exception) {
                // Handled
            }
        }
    }

    private val viewModel: FloatingBallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeInstance = this
        enableEdgeToEdge()

        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.appTasks?.forEach { task ->
                task.setExcludeFromRecents(true)
            }
        } catch (e: Exception) {
            // Handled
        }

        setContent {
            MyApplicationTheme {
                FloatingBallScreen(
                    viewModel = viewModel,
                    onExitAndClearRecents = {
                        finishAndRemoveTask()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }

    override fun onPause() {
        super.onPause()
        // If floating ball service is running, purge MainActivity from Recents immediately when leaving
        if (viewModel.settings.value.isServiceEnabled) {
            finishAndRemoveTask()
        }
    }

    override fun onStop() {
        super.onStop()
        if (viewModel.settings.value.isServiceEnabled) {
            finishAndRemoveTask()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance == this) {
            activeInstance = null
        }
    }
}
