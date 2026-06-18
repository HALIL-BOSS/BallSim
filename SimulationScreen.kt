package com.example.bouncingballsim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SimulationScreen() {
    // Параметры физической модели,
    // управляемые пользователем через слайдеры
    var gravity by remember { mutableFloatStateOf(2000f) }      // ускорение свободного падения
    var restitution by remember { mutableFloatStateOf(0.75f) }  // коэффициент восстановления (упругость)
    var friction by remember { mutableFloatStateOf(0.15f) }     // коэффициент трения при контакте
    var airDrag by remember { mutableFloatStateOf(0.20f) }      // коэффициент линейного сопротивления среды

    // Начальная угловая скорость (спин) шара
    var spin by remember { mutableFloatStateOf(0f) }

    // Флаг: идёт ли сейчас симуляция (true) или пауза (false)
    var running by remember { mutableStateOf(false) }

    // Флаг: учитывается ли вращение шара в физической модели
    var rotationEnabled by remember { mutableStateOf(true) }

    // Счётчик для триггера перерисовки Canvas
    var renderTick by remember { mutableIntStateOf(0) }

    // Список точек траектории (для отрисовки пути шара)
    val trajectory = remember { mutableStateListOf<Vec2>() }

    // Размер "мира" в условных единицах (координаты физики)
    // По высоте мир фиксирован: 0..1000
    val worldWidth = 1080f
    val worldHeight = 1000f

    // Инициализация физического движка в координатах "мира"
    val engine = remember {
        val rectLeft = 650f
        val rectTop = 420f
        val rectRight = 900f
        val rectBottom = 520f

        PhysicsEngine(
            // Начальное положение и скорость шара, радиус
            ball = Ball(p = Vec2(280f, 250f), v = Vec2(0f, 0f), r = 31f),
            // Набор препятствий сцены
            obstacles = listOf(
                // Наклонный "пол" — лежит примерно на y = worldHeight
                Obstacle.Segment(
                    a = Vec2(0f, worldHeight),
                    b = Vec2(worldWidth, worldHeight - 2f) // чуть наклонён
                ),
                // Наклонный сегмент (горка)
                Obstacle.Segment(
                    Vec2(120f, 900f),
                    Vec2(900f, 700f)
                ),
                // Прямоугольное препятствие
                Obstacle.Rect(
                    left = rectLeft,
                    top = rectTop,
                    right = rectRight,
                    bottom = rectBottom
                ),
                // Слегка "неровная" верхняя граница прямоугольника
                Obstacle.Segment(
                    a = Vec2(rectLeft,  rectTop),
                    b = Vec2(rectRight, rectTop - 3f)
                ),
                // Круговое препятствие
                Obstacle.Circle(
                    c = Vec2(250f, 650f),
                    r = 60f
                )
            ),
            // Физические параметры (будут обновляться из UI)
            params = PhysicsParams(),
            // Изначально вращение включено
            rotationEnabled = true
        )
    }

    // Цикл физического моделирования с фиксированным шагом по времени.
    // Запускается в корутине и работает, пока composable "жив" (isActive == true).
    LaunchedEffect(running) {
        val fixedDt = 1f / 120f  // шаг интегрирования 1/120 секунды
        while (isActive) {
            // Передаём текущие значения параметров из UI в физический движок
            engine.params.gravity = gravity
            engine.params.restitution = restitution
            engine.params.friction = friction
            engine.params.airDrag = airDrag
            engine.rotationEnabled = rotationEnabled

            if (running) {
                // Несколько шагов физики за один проход цикла для стабильности
                repeat(2) {
                    engine.step(fixedDt, worldWidth, worldHeight)
                }
                // Сохраняем точку траектории для последующей отрисовки
                trajectory.add(engine.ball.p.copy())
                // Ограничиваем длину списка траектории
                if (trajectory.size > 1000) trajectory.removeAt(0)
                // Запрашиваем перерисовку Canvas
                renderTick++
            }

            // Небольшая задержка, чтобы не перегружать UI-поток
            delay(8L)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Область симуляции (поле с шаром) фиксированной высоты
        Box(
            Modifier
                .fillMaxWidth()
                .height(400.dp)      // высота области симуляции на экране
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    // Обработка жестов перетаскивания шара
                    .pointerInput(running) {
                        // Во время симуляции перетаскивание отключено
                        if (running) return@pointerInput

                        detectDragGestures(
                            // Начало перетаскивания
                            onDragStart = { p ->
                                // Текущий размер канваса (в пикселях экрана)
                                val canvasSize = this.size
                                // Масштаб: мир -> экран
                                val scaleX = canvasSize.width / worldWidth
                                val scaleY = canvasSize.height / worldHeight

                                // Переводим экранные координаты касания в координаты мира
                                val worldTouch = Vec2(
                                    x = p.x / scaleX,
                                    y = p.y / scaleY
                                )

                                // Проверяем, попал ли пользователь пальцем по шару
                                val bp = engine.ball.p
                                val dx = worldTouch.x - bp.x
                                val dy = worldTouch.y - bp.y
                                // Допускаем небольшой "запас" по радиусу для удобства касания
                                val rr = engine.ball.r * 2.2f
                                val hit = (dx * dx + dy * dy) <= rr * rr

                                if (hit) {
                                    // Обнуляем скорость и угловую скорость при "захвате" шара
                                    engine.ball.v = Vec2(0f, 0f)
                                    engine.ball.omega = 0f
                                    // Ограничиваем положение шара границами мира
                                    val x = clamp(worldTouch.x, engine.ball.r, worldWidth - engine.ball.r)
                                    val y = clamp(worldTouch.y, engine.ball.r, worldHeight - engine.ball.r)
                                    engine.ball.p = Vec2(x, y)
                                    renderTick++
                                }
                            },
                            // Процесс перетаскивания
                            onDrag = { change, _ ->
                                val canvasSize = this.size
                                val scaleX = canvasSize.width / worldWidth
                                val scaleY = canvasSize.height / worldHeight

                                val worldTouch = Vec2(
                                    x = change.position.x / scaleX,
                                    y = change.position.y / scaleY
                                )

                                // Координаты шара также ограничиваем областью моделирования
                                val x = clamp(worldTouch.x, engine.ball.r, worldWidth - engine.ball.r)
                                val y = clamp(worldTouch.y, engine.ball.r, worldHeight - engine.ball.r)
                                engine.ball.p = Vec2(x, y)
                                // При ручном перемещении скорость и угловая скорость сбрасываются
                                engine.ball.v = Vec2(0f, 0f)
                                engine.ball.omega = 0f
                                renderTick++
                                // Сообщаем системе ввода, что событие обработано
                                change.consume()
                            },
                            // Окончание перетаскивания
                            onDragEnd = {
                                engine.ball.v = Vec2(0f, 0f)
                                engine.ball.omega = 0f
                                renderTick++
                            }
                        )
                    }
            ) {
                // Делаем Composable зависимым от renderTick (для перерисовки)
                @Suppress("UNUSED_VARIABLE")
                val _rt = renderTick

                val canvasW = size.width
                val canvasH = size.height

                // Масштаб: координаты мира -> координаты экрана
                val scaleX = canvasW / worldWidth
                val scaleY = canvasH / worldHeight

                // Вспомогательная функция перевода точки мира в экранные координаты
                fun worldToScreen(v: Vec2): Offset =
                    Offset(v.x * scaleX, v.y * scaleY)

                // Заливка фона
                drawRect(color = Color(0xFFF5F5F5))

                // Отрисовка вспомогательной сетки по миру с шагом 100
                val gridStep = 100f
                val gridColor = Color(0xFFE0E0E0)
                val maxX = (worldWidth / gridStep).toInt()
                val maxY = (worldHeight / gridStep).toInt()

                // Вертикальные линии сетки
                for (x in 0..maxX) {
                    val sx = x * gridStep * scaleX
                    drawLine(
                        color = gridColor,
                        start = Offset(sx, 0f),
                        end = Offset(sx, canvasH),
                        strokeWidth = 1f
                    )
                }

                // Горизонтальные линии сетки
                for (y in 0..maxY) {
                    val sy = y * gridStep * scaleY
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, sy),
                        end = Offset(canvasW, sy),
                        strokeWidth = 1f
                    )
                }

                val obsColor = Color(0xFF424242)

                // Отрисовка препятствий (сегменты, прямоугольники, круги)
                engine.obstacles.forEach { o ->
                    when (o) {
                        is Obstacle.Segment -> drawLine(
                            color = obsColor,
                            start = worldToScreen(o.a),
                            end = worldToScreen(o.b),
                            strokeWidth = 6f
                        )

                        is Obstacle.Rect -> drawRect(
                            color = obsColor,
                            topLeft = worldToScreen(Vec2(o.left, o.top)),
                            size = Size(
                                (o.right - o.left) * scaleX,
                                (o.bottom - o.top) * scaleY
                            ),
                            style = Stroke(width = 5f)
                        )

                        is Obstacle.Circle -> drawCircle(
                            color = obsColor,
                            radius = o.r * ((scaleX + scaleY) * 0.5f),
                            center = worldToScreen(o.c),
                            style = Stroke(width = 5f)
                        )
                    }
                }

                // Отрисовка шара
                val ballCenter = worldToScreen(engine.ball.p)
                // Радиус шара в экранных координатах (с учётом среднего масштаба по осям)
                val ballRadius = engine.ball.r * ((scaleX + scaleY) * 0.5f)

                // Тело шара (заливка)
                drawCircle(
                    color = Color(0xFF1E88E5),
                    radius = ballRadius,
                    center = ballCenter
                )

                // Маркер вращения: линия от центра шара,
                // показывающая текущий угол поворота
                if (rotationEnabled) {
                    // Угол берём из поля angle, знак минус для поворота в экранных координатах
                    val angle = -engine.ball.angle
                    val markerLen = ballRadius * 0.8f
                    val markerEnd = Offset(
                        x = ballCenter.x + markerLen * cos(angle),
                        y = ballCenter.y + markerLen * sin(angle)
                    )

                    drawLine(
                        color = Color.White,
                        start = ballCenter,
                        end = markerEnd,
                        strokeWidth = 4f
                    )
                }

                // Отрисовка траектории движения шара
                for (i in 1 until trajectory.size) {
                    val p1 = worldToScreen(trajectory[i - 1])
                    val p2 = worldToScreen(trajectory[i])
                    drawLine(
                        color = Color(0xFF1565C0),
                        start = p1,
                        end = p2,
                        strokeWidth = 3f
                    )
                }

                // Контур шара
                drawCircle(
                    color = Color(0xFF0D47A1),
                    radius = ballRadius,
                    center = ballCenter,
                    style = Stroke(width = 3f)
                )
            }
        }

        // Панель управления параметрами под областью симуляции
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            // Кнопки управления симуляцией и вращением
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Старт / Пауза
                Button(onClick = {
                    running = !running
                    if (running) {
                        // При запуске обнуляем линейную скорость
                        engine.ball.v = Vec2(0f, 0f)
                        // Начальную угловую скорость задаём из параметра spin (если вращение включено)
                        engine.ball.omega = if (rotationEnabled) spin else 0f
                    }
                    renderTick++
                }) {
                    Text(if (running) "Pause" else "Start")
                }

                // Сброс начального состояния
                Button(onClick = {
                    running = false
                    // Возвращаем шар в исходную позицию
                    engine.ball.p = Vec2(280f, 250f)
                    engine.ball.v = Vec2(0f, 0f)
                    engine.ball.omega = if (rotationEnabled) spin else 0f
                    trajectory.clear()
                    renderTick++
                }) {
                    Text("Reset")
                }

                // Включение / выключение учёта вращения
                Button(onClick = {
                    rotationEnabled = !rotationEnabled
                    if (!rotationEnabled) {
                        // Если вращение выключено, угловую скорость обнуляем
                        engine.ball.omega = 0f
                    }
                    renderTick++
                }) {
                    Text(if (rotationEnabled) "Rotation: ON" else "Rotation: OFF")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Слайдеры для изменения параметров физической модели
            ParamSlider("Gravity", gravity, 0f, 4000f) { gravity = it }
            ParamSlider("Restitution (e)", restitution, 0f, 1f) { restitution = it }
            ParamSlider("Friction (μ)", friction, 0f, 0.6f) { friction = it }
            ParamSlider("Air drag", airDrag, 0f, 1.5f) { airDrag = it }
            ParamSlider("Spin (ω, rad/s)", spin, -20f, 20f) { spin = it }
        }
    }
}

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        // Подпись параметра и текущее значение
        Text("$label: ${"%.2f".format(value)}")
        // Сам слайдер, изменяющий значение параметра в заданном диапазоне
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = min..max
        )
    }
}
