package com.example.bouncingballsim

import kotlin.math.*

// Набор настраиваемых физических параметров модели.
// Значения обновляются из пользовательского интерфейса (слайдеры).
data class PhysicsParams(
    var gravity: Float = 2000f,      // ускорение свободного падения, px/s^2
    var restitution: Float = 0.75f,  // коэффициент восстановления (упругость) e
    var friction: Float = 0.15f,     // коэффициент трения μ
    var airDrag: Float = 0.20f       // коэффициент линейного сопротивления среды
)

// Класс физического движка, отвечающий за обновление состояния шара
// и обработку его столкновений с препятствиями.
class PhysicsEngine(
    var ball: Ball,                          // текущее состояние шара
    var obstacles: List<Obstacle> = emptyList(), // список препятствий сцены
    var params: PhysicsParams = PhysicsParams(), // физические параметры
    var rotationEnabled: Boolean = true          // флаг: учитывать ли вращение
) {

    // Один шаг интегрирования с шагом времени dt.
    // worldW, worldH — размеры "мира" в координатах модели.
    fun step(dt: Float, worldW: Float, worldH: Float) {
        // Внешние силы:
        // гравитация (вниз по оси y)
        val g = Vec2(0f, params.gravity)
        // сопротивление среды ~ -v (модель линейного сопротивления)
        val dragA = ball.v * (-params.airDrag)
        // суммарное ускорение
        val a = g + dragA

        // Полунеявный метод Эйлера (semi-implicit Euler) для линейного движения:
        // сначала обновляем скорость, затем положение
        ball.v = ball.v + a * dt
        ball.p = ball.p + ball.v * dt

        // Обновляем угол поворота шара при включённом вращении:
        // angle_{n+1} = angle_n + ω * dt
        if (rotationEnabled) {
            ball.angle += ball.omega * dt
        }

        // Столкновения со "стенами" прямоугольной области
        collideWithWalls(worldW, worldH)

        // Столкновения шара с каждым препятствием сцены
        for (o in obstacles) {
            when (o) {
                is Obstacle.Segment -> collideCircleSegment(o.a, o.b)
                is Obstacle.Rect    -> collideCircleRect(o.left, o.top, o.right, o.bottom)
                is Obstacle.Circle  -> collideCircleCircleObstacle(o.c, o.r)
            }
        }
    }

    /**
     * Импульсная модель столкновения шара с поверхностью.
     *
     * normal      — единичная нормаль в точке контакта (направлена от поверхности к шару),
     * penetration — глубина проникновения шара в препятствие.
     *
     * Схема:
     * 1) Выталкиваем центр шара вдоль нормали на величину penetration.
     * 2) Вычисляем скорость точки контакта и её компоненты по нормали и касательной.
     * 3) Находим нормальный импульс Jn (упругий отскок с коэффициентом e).
     * 4) Находим касательный импульс Jt, ограниченный законом Кулона (трение).
     * 5) Корректируем линейную скорость шара и, при включённом вращении, угловую скорость.
     */
    private fun applyCollisionResponse(normal: Vec2, penetration: Float) {
        // 1. Выталкивание центра шара из препятствия вдоль нормали
        ball.p = ball.p + normal * penetration

        // Проекция линейной скорости на нормаль (скорость по нормали до удара)
        val vn = ball.v.dot(normal)
        if (vn >= 0f) {
            // Если шар уже движется от поверхности (vn >= 0), дополнительная коррекция не нужна
            return
        }

        // Модель шара: твёрдое тело с массой m = 1
        val m = 1f
        val rBall = ball.r

        // Момент инерции однородного диска относительно оси, перпендикулярной плоскости:
        // I = 0.5 * m * r^2
        val inertia = 0.5f * m * rBall * rBall

        // Вектор касательной — перпендикуляр к нормали в плоскости (поворот на 90 градусов)
        val tangent = Vec2(-normal.y, normal.x)

        // 2. Скорость точки контакта:
        // v_contact = v + v_rot,
        // где v — линейная скорость центра,
        //     v_rot — скорость от вращения вдоль касательной: ω * r * tangent
        val vContactLinear = ball.v
        val vContactRot = tangent * (ball.omega * rBall)
        val vContact = vContactLinear + vContactRot

        // Разложение скорости точки контакта по нормали и касательной
        val vRelN = vContact.dot(normal)     // нормальная составляющая
        val vRelT = vContact.dot(tangent)    // касательная составляющая

        // --- 1) Импульс по нормали (упругий отскок) ---
        val e = params.restitution
        // При массе m = 1 нормальный импульс: Jn = -(1 + e) * vRelN
        val Jn = -(1f + e) * vRelN

        // Вклад нормального импульса в линейную скорость: Δv = (Jn / m) * normal
        val impulseN = normal * (Jn / m)
        ball.v += impulseN

        // --- 2) Трение по касательной (кулоновская модель) ---
        val mu = params.friction

        // Эффективная масса по касательной с учётом вращения:
        // 1 / m_effT = 1/m + (r^2 / I)
        val invMassT = 1f / m + (rBall * rBall) / inertia
        val mEffT = 1f / invMassT

        // Импульс, который бы полностью погасил касательную скорость точки контакта
        var Jt = -vRelT * mEffT

        // Ограничиваем модуль касательного импульса силой трения:
        // |Jt| <= μ * |Jn|
        val maxJt = mu * abs(Jn)
        if (abs(Jt) > maxJt) {
            Jt = maxJt * sign(Jt)
        }

        // Применяем касательный импульс к линейной скорости шара
        val impulseT = tangent * (Jt / m)
        ball.v += impulseT

        // Момент от касательного импульса: τ = r * Jt
        // Изменение угловой скорости: dω = τ / I
        val torque = rBall * Jt
        val dOmega = torque / inertia

        // Учитываем изменение угловой скорости только если вращение включено в модели
        if (rotationEnabled) {
            ball.omega += dOmega
        }
    }

    // Столкновения шара с прямоугольной "рамкой" мира.
    // Используются только вертикальные и верхняя граница;
    // нижний "пол" реализован отдельным наклонным сегментом.
    private fun collideWithWalls(w: Float, h: Float) {
        val r = ball.r

        // Левая стена: x < r
        if (ball.p.x < r) {
            applyCollisionResponse(Vec2(1f, 0f), r - ball.p.x)
        }

        // Правая стена: x > w - r
        if (ball.p.x > w - r) {
            applyCollisionResponse(Vec2(-1f, 0f), ball.p.x - (w - r))
        }

        // Потолок: y < r
        if (ball.p.y < r) {
            applyCollisionResponse(Vec2(0f, 1f), r - ball.p.y)
        }

        // Нижняя прямоугольная граница (y > h - r) не используется,
        // так как "пол" задаётся отдельным наклонным сегментом.
        // if (ball.p.y > h - r) {
        //     applyCollisionResponse(Vec2(0f, -1f), ball.p.y - (h - r))
        // }
    }

    // Столкновение шара (окружности) с отрезком.
    // Находим ближайшую к центру шара точку на отрезке и сравниваем расстояние с радиусом.
    private fun collideCircleSegment(a: Vec2, b: Vec2) {
        // Ближайшая точка на сегменте AB к центру шара
        val closest = closestPointOnSegment(ball.p, a, b)
        // Вектор от ближайшей точки до центра шара
        val d = ball.p - closest
        val dist2 = d.len2()
        val r = ball.r

        // Проверяем пересечение по условию расстояния: dist < r
        if (dist2 < r * r) {
            // Реальное расстояние с защитой от деления на ноль
            val dist = sqrt(dist2.coerceAtLeast(1e-6f))

            // Нормаль от поверхности к шару (из ближайшей точки к центру шара)
            val n = if (dist > 1e-6f) {
                Vec2(d.x / dist, d.y / dist)
            } else {
                // Если центр практически совпал с опорной точкой, берём нормаль "по умолчанию"
                Vec2(0f, -1f)
            }

            // Глубина проникновения
            val penetration = r - dist
            // Обработка реакции на столкновение
            applyCollisionResponse(n, penetration)
        }
    }

    // Столкновение шара с прямоугольником.
    // Сначала находим ближайшую к центру шара точку прямоугольника,
    // затем проверяем расстояние до неё.
    private fun collideCircleRect(l: Float, t: Float, r: Float, b: Float) {
        // "Зажимаем" координаты центра шара в пределах сторон прямоугольника
        val cx = clamp(ball.p.x, l, r)
        val cy = clamp(ball.p.y, t, b)
        val closest = Vec2(cx, cy)

        // Вектор от ближайшей точки прямоугольника к центру шара
        val d = ball.p - closest
        val dist2 = d.len2()

        // Условие пересечения: расстояние меньше радиуса шара
        if (dist2 < ball.r * ball.r) {
            val dist = sqrt(dist2.coerceAtLeast(1e-6f))

            // Нормаль: направлена от прямоугольника к центру шара
            val n = if (dist > 1e-6f) {
                d * (1f / dist)
            } else {
                Vec2(0f, -1f)
            }

            val penetration = ball.r - dist
            applyCollisionResponse(n, penetration)
        }
    }

    // Столкновение шара с круговым препятствием.
    // Проверяем расстояние между центрами двух окружностей.
    private fun collideCircleCircleObstacle(c: Vec2, rObs: Float) {
        // Вектор от центра препятствия к центру шара
        val d = ball.p - c
        val rr = ball.r + rObs    // сумма радиусов
        val dist2 = d.len2()

        // Условие пересечения: расстояние между центрами меньше суммы радиусов
        if (dist2 < rr * rr) {
            val dist = sqrt(dist2.coerceAtLeast(1e-6f))

            // Нормаль: направление от препятствия к шару
            val n = if (dist > 1e-6f) {
                d * (1f / dist)
            } else {
                Vec2(0f, -1f)
            }

            val penetration = rr - dist
            applyCollisionResponse(n, penetration)
        }
    }
}
