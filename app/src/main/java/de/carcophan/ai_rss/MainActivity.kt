package de.carcophan.ai_rss

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.carcophan.ai_rss.ui.RssScreen
import de.carcophan.ai_rss.ui.theme.AirssTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AirssTheme {
                RssScreen()
            }
        }
    }
}