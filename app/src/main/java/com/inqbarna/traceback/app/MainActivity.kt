package com.inqbarna.traceback.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Text

/**
 * @author David García (david.garcia@inqbarna.com)
 * @version 1.0 17/6/25
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Text("Hello, Traceback!")
        }
    }
}
