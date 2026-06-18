package com.example.bouncingballsim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.bouncingballsim.ui.theme.BouncingBallSimTheme

// Главная активность приложения. Содержит единственный экран с симуляцией.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Разрешаем отрисовку "под" системные панели (статус-бар, навигация)
        enableEdgeToEdge()

        // Устанавливаем содержимое экрана с помощью Jetpack Compose
        setContent {
            // Применяем тему оформления приложения
            BouncingBallSimTheme {
                // Базовый контейнер с учётом системных отступов
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Внутри Scaffold размещаем экран с симуляцией шара
                    Box(Modifier.padding(innerPadding)) {
                        SimulationScreen()
                    }
                }
            }
        }
    }
}
