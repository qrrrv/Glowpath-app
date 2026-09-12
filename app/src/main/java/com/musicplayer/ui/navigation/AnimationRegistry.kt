package com.musicplayer.ui.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin

/**
 * Централизованный реестр всех анимаций и переходов приложения.
 *
 * Организован по категориям:
 *  • [Durations]       — длительности
 *  • [Springs]         — spring-спецификации
 *  • [Screen]          — переходы между экранами (навигация)
 *  • [Player]          — анимации плеера (появление, коллапс)
 *  • [Ui]              — анимации UI-элементов (кнопки, карточки, overlay)
 */
object AnimationRegistry {

    // ── Durations ─────────────────────────────────────────────────────────────

    object Durations {
        /** Стандартный переход экранов */
        const val SCREEN_TRANSITION_MS = 500

        /** Быстрое появление/исчезновение элементов */
        const val FAST_MS = 200

        /** Средняя скорость для карточек, диалогов */
        const val MEDIUM_MS = 300

        /** Медленные эффекты — плеер, overlay */
        const val SLOW_MS = 400

        /** AI-вращение иконки (бесконечная анимация) */
        const val AI_ICON_ROTATION_MS = 3000

        /** Пульс шкалы AI-иконки */
        const val AI_ICON_PULSE_MS = 800
    }

    // ── Springs ───────────────────────────────────────────────────────────────

    object Springs {
        /** Мягкий пружинный отскок — для кнопок и карточек */
        val BOUNCY = spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        )

        /** Без отскока — для размеров и радиусов */
        val NO_BOUNCE = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessMedium
        )

        /** Вязкий — медленный и плавный */
        val LOW_STIFFNESS = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessLow
        )
    }

    // ── Easing curves (Telegram-style) ────────────────────────────────────────

    object Easing {
        /**
         * EaseOutQuint — быстрый старт, очень плавное торможение.
         * Используется в Telegram (ViewPagerFixed) для свайп-переходов между папками.
         * Аналог CubicBezierInterpolator.EASE_OUT_QUINT
         */
        val OUT_QUINT = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)

        /**
         * EaseOutCubic — чуть менее выраженный OUT_QUINT.
         * Для элементов UI — индикатор таба, пилюля навбара.
         */
        val OUT_CUBIC = CubicBezierEasing(0.33f, 1.0f, 0.68f, 1.0f)

        /**
         * Material3 Standard — стандартный переход экранов.
         */
        val STANDARD = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

        /**
         * Material3 Emphasized — выразительный переход (Fast → Eased out).
         * Используется в Telegram для slideInHorizontally папок.
         */
        val EMPHASIZED = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

        /**
         * Стандартный tab easing (Telegram: FilterTabsView).
         */
        val TAB = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    }

    // ── Screen Transitions ────────────────────────────────────────────────────

    object Screen {
        // Материальный easing: быстрый старт, мягкий тормоз
        private val materialEasing  = Easing.STANDARD
        // Easing для tab-переходов: чуть бодрее
        private val tabEasing       = Easing.TAB

        /**
         * Push-переход: новый экран въезжает справа (Material 3 Shared Axis X)
         */
        fun enter() = slideInHorizontally(
            animationSpec = tween(420, easing = materialEasing),
            initialOffsetX = { (it * 0.25f).toInt() }
        ) + fadeIn(
            animationSpec = tween(200, delayMillis = 60)
        )

        /**
         * Push-переход: текущий экран уходит влево с лёгким затуханием
         */
        fun exit() = slideOutHorizontally(
            animationSpec = tween(420, easing = materialEasing),
            targetOffsetX = { -(it * 0.12f).toInt() }
        ) + fadeOut(
            animationSpec = tween(200)
        )

        /**
         * Pop-переход: предыдущий экран возвращается слева
         */
        fun popEnter() = slideInHorizontally(
            animationSpec = tween(380, easing = materialEasing),
            initialOffsetX = { -(it * 0.12f).toInt() }
        ) + fadeIn(
            animationSpec = tween(200, delayMillis = 50)
        )

        /**
         * Pop-переход: закрываемый экран уезжает вправо
         */
        fun popExit() = slideOutHorizontally(
            animationSpec = tween(380, easing = materialEasing),
            targetOffsetX = { (it * 0.25f).toInt() }
        ) + fadeOut(
            animationSpec = tween(200)
        )

        /**
         * Tab-переход: новая вкладка справа входит → едет влево
         * Telegram-style: EaseOutQuint + лёгкий scale
         */
        fun tabEnterFromRight() = slideInHorizontally(
            animationSpec = tween(280, easing = Easing.OUT_CUBIC),
            initialOffsetX = { (it * 0.18f).toInt() }
        ) + fadeIn(animationSpec = tween(180, easing = Easing.OUT_CUBIC))

        /**
         * Tab-переход: текущая вкладка уходит влево
         */
        fun tabExitToLeft() = slideOutHorizontally(
            animationSpec = tween(250, easing = Easing.OUT_CUBIC),
            targetOffsetX = { -(it * 0.16f).toInt() }
        ) + fadeOut(animationSpec = tween(140))

        /**
         * Tab-переход: новая вкладка слева входит → едет вправо
         */
        fun tabEnterFromLeft() = slideInHorizontally(
            animationSpec = tween(280, easing = Easing.OUT_CUBIC),
            initialOffsetX = { -(it * 0.18f).toInt() }
        ) + fadeIn(animationSpec = tween(180, easing = Easing.OUT_CUBIC))

        /**
         * Tab-переход: текущая вкладка уходит вправо
         */
        fun tabExitToRight() = slideOutHorizontally(
            animationSpec = tween(250, easing = Easing.OUT_CUBIC),
            targetOffsetX = { (it * 0.16f).toInt() }
        ) + fadeOut(animationSpec = tween(140))

        /**
         * Setup → Main App: главный контент появляется с масштабированием.
         * Паттерн из MainActivity (PixelPlay).
         */
        fun setupToMain() = scaleIn(
            initialScale  = 0.8f,
            animationSpec = tween(Durations.SLOW_MS)
        ) + fadeIn(
            animationSpec = tween(Durations.SLOW_MS)
        )

        /**
         * Setup → Main App: экран установки уходит влево.
         */
        fun setupExit() = slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(Durations.SLOW_MS)
        ) + fadeOut(
            animationSpec = tween(Durations.SLOW_MS)
        )
    }

    // ── Player Animations ─────────────────────────────────────────────────────

    object Player {
        /**
         * Mini-player / snackbar появляется снизу.
         */
        fun slideUpEnter() = slideInVertically(
            initialOffsetY = { it },
            animationSpec  = tween(Durations.MEDIUM_MS, easing = FastOutSlowInEasing)
        ) + fadeIn(
            animationSpec = tween(Durations.MEDIUM_MS)
        )

        /**
         * Mini-player / snackbar уходит вверх.
         */
        fun slideUpExit() = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(Durations.MEDIUM_MS, easing = FastOutSlowInEasing)
        ) + fadeOut(
            animationSpec = tween(Durations.MEDIUM_MS)
        )

        /**
         * Элемент уходит вниз (dismiss/collapse).
         */
        fun slideDownExit() = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(Durations.MEDIUM_MS, easing = FastOutSlowInEasing)
        ) + fadeOut(
            animationSpec = tween(Durations.MEDIUM_MS)
        )
    }

    // ── UI Elements ───────────────────────────────────────────────────────────

    object Ui {
        /**
         * Появление контента (fade + scale) — ошибки, подсказки, карточки.
         * Паттерн из AiPlaylistSheet (PixelPlay).
         */
        fun popIn() = fadeIn(
            animationSpec = tween(Durations.FAST_MS)
        ) + scaleIn(
            initialScale  = 0.9f,
            animationSpec = tween(Durations.FAST_MS)
        )

        /**
         * Исчезновение контента.
         */
        fun popOut() = fadeOut(
            animationSpec = tween(Durations.FAST_MS)
        ) + scaleOut(
            targetScale   = 0.9f,
            animationSpec = tween(Durations.FAST_MS)
        )

        /**
         * Простое затухание — для overlay, фонов.
         */
        fun fadeIn(durationMs: Int = Durations.MEDIUM_MS) =
            androidx.compose.animation.fadeIn(tween(durationMs, easing = LinearEasing))

        fun fadeOut(durationMs: Int = Durations.MEDIUM_MS) =
            androidx.compose.animation.fadeOut(tween(durationMs, easing = LinearEasing))
    }
}
