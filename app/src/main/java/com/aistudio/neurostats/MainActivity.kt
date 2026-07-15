package com.aistudio.neurostats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.aistudio.neurostats.ui.Dashboard
import com.aistudio.neurostats.ui.MMSINeuroDashboardScreen
import com.aistudio.neurostats.ui.SessionDetail
import com.aistudio.neurostats.ui.SessionDetailScreen
import com.aistudio.neurostats.ui.theme.NeuroStatsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeuroStatsTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        NavHost(navController = navController, startDestination = Dashboard) {
                            composable<Dashboard> {
                                MMSINeuroDashboardScreen(onNavigateToSession = { sessionId ->
                                    navController.navigate(SessionDetail(sessionId))
                                })
                            }
                            composable<SessionDetail> { backStackEntry ->
                                val detail: SessionDetail = backStackEntry.toRoute()
                                SessionDetailScreen(
                                    sessionId = detail.sessionId,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
