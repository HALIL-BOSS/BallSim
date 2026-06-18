package com.example.bouncingballsim

// Запечатанный (sealed) класс для описания препятствий в сцене.
// Позволяет хранить разные типы геометрии в одном списке и
// обрабатывать их через when.
sealed class Obstacle {

    // Наклонный линейный сегмент (отрезок) от точки a до точки b.
    // Используется для моделирования "пола", наклонных плоскостей и т.п.
    data class Segment(val a: Vec2, val b: Vec2) : Obstacle()

    // Прямоугольное препятствие, заданное координатами сторон.
    // left, top, right, bottom — координаты в системе "мира".
    data class Rect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) : Obstacle()

    // Круговое препятствие с центром c и радиусом r.
    data class Circle(
        val c: Vec2,
        val r: Float
    ) : Obstacle()
}

// Модель шара, который движется и сталкивается с препятствиями.
data class Ball(
    var p: Vec2,             // положение центра шара
    var v: Vec2,             // линейная скорость центра шара
    val r: Float = 24f,      // радиус шара
    var angle: Float = 0f,   // угол поворота (в радианах)
    var omega: Float = 0f    // угловая скорость (рад/с)
)
