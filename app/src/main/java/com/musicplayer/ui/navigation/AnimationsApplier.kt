package com.musicplayer.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import com.musicplayer.data.AnimParams

/**
 * Применяет настройки анимаций из PlayerSettings к навигационным переходам.
 * Каждый метод принимает индекс (из настроек) и AnimParams для точной настройки.
 */
object AnimationsApplier {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun easingFor(idx: Int): Easing = when (idx) {
        1 -> FastOutSlowInEasing
        2 -> FastOutLinearInEasing
        3 -> LinearEasing
        4 -> CubicBezierEasing(0.2f, 1.25f, 0.38f, 1f)
        5 -> CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
        else -> FastOutSlowInEasing
    }

    private fun dampingFor(idx: Int): Float = when (idx) {
        1 -> Spring.DampingRatioLowBouncy
        2 -> Spring.DampingRatioHighBouncy
        3 -> Spring.DampingRatioNoBouncy
        else -> Spring.DampingRatioMediumBouncy
    }

    private fun stiffnessFor(idx: Int): Float = when (idx) {
        1 -> Spring.StiffnessMediumLow
        2 -> Spring.StiffnessHigh
        3 -> Spring.StiffnessLow
        else -> Spring.StiffnessMedium
    }

    // For Float animations (scale, alpha)
    private fun springForFloat(dampingIdx: Int, stiffnessIdx: Int): SpringSpec<Float> =
        spring(dampingRatio = dampingFor(dampingIdx), stiffness = stiffnessFor(stiffnessIdx))

    // For IntOffset animations (slide)
    private fun springForInt(dampingIdx: Int, stiffnessIdx: Int): SpringSpec<IntOffset> =
        spring(dampingRatio = dampingFor(dampingIdx), stiffness = stiffnessFor(stiffnessIdx))

    @Suppress("UNUSED")
    private fun springFor(dampingIdx: Int, stiffnessIdx: Int): SpringSpec<Float> =
        springForFloat(dampingIdx, stiffnessIdx)

    private fun tweenMs(baseMs: Int, speed: Float): Int =
        (baseMs / speed.coerceIn(0.2f, 4f)).toInt().coerceAtLeast(50)

    // ── Открытие плеера ───────────────────────────────────────────────────────

    fun playerEnter(idx: Int, p: AnimParams = AnimParams()): EnterTransition {
        val d = tweenMs(340, p.playerEnterSpeed)
        val spI = springForInt(p.playerEnterDamping, p.playerEnterStiffness)
        val spF = springForFloat(p.playerEnterDamping, p.playerEnterStiffness)
        val e = easingFor(p.playerEnterEasing)
        return when (idx) {
            0  -> slideInVertically(spI) { it } + fadeIn(tween(tweenMs(250, p.playerEnterSpeed)))
            1  -> scaleIn(spF, initialScale = 0.3f) + fadeIn(tween(tweenMs(200, p.playerEnterSpeed)))
            2  -> fadeIn(tween(tweenMs(380, p.playerEnterSpeed), easing = e))
            3  -> slideInHorizontally(tween(d, easing = e)) { it } + fadeIn(tween(tweenMs(300, p.playerEnterSpeed)))
            4  -> slideInHorizontally(tween(d, easing = e)) { it } + fadeIn(tween(tweenMs(280, p.playerEnterSpeed)))
            5  -> slideInVertically(springForInt(1, p.playerEnterStiffness)) { it } + fadeIn(tween(tweenMs(200, p.playerEnterSpeed)))
            6  -> slideInVertically(springForInt(1, p.playerEnterStiffness)) { it } +
                  scaleIn(springForFloat(1, p.playerEnterStiffness), initialScale = 0.7f)
            7  -> scaleIn(springForFloat(p.playerEnterDamping, 2), initialScale = 2.5f) + fadeIn(tween(tweenMs(250, p.playerEnterSpeed)))
            8  -> slideInVertically(tween(d, easing = e)) { it } + scaleIn(tween(d), initialScale = 0.95f)
            9  -> slideInHorizontally(spI) { it } + scaleIn(spF, initialScale = 0.8f) + fadeIn(tween(tweenMs(250, p.playerEnterSpeed)))
            10 -> fadeIn(tween(tweenMs(500, p.playerEnterSpeed), easing = e))
            11 -> slideInHorizontally(tween(d, easing = e)) { it } + scaleIn(tween(d), initialScale = 0.9f) + fadeIn(tween(tweenMs(280, p.playerEnterSpeed)))
            12 -> slideInHorizontally(tween(d, easing = e)) { (it * 0.28f).toInt() } +
                  slideInVertically(tween(d, easing = e)) { (it * 0.22f).toInt() } +
                  fadeIn(tween(tweenMs(260, p.playerEnterSpeed)))
            13 -> slideInVertically(tween(d, easing = e)) { (it * 0.32f).toInt() } +
                  scaleIn(tween(d, easing = e), initialScale = 0.88f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                  fadeIn(tween(tweenMs(240, p.playerEnterSpeed)))
            14 -> scaleIn(tween(d, easing = e), initialScale = 0.72f, transformOrigin = TransformOrigin(0.5f, 0.35f)) +
                  fadeIn(tween(tweenMs(300, p.playerEnterSpeed), easing = e))
            else -> slideInVertically(spI) { it } + fadeIn(tween(tweenMs(250, p.playerEnterSpeed)))
        }
    }

    fun playerExit(): ExitTransition =
        slideOutVertically(tween(360, easing = FastOutSlowInEasing)) { (it * 0.08f).toInt() } +
        scaleOut(tween(360, easing = FastOutSlowInEasing), targetScale = 0.96f, transformOrigin = TransformOrigin(0.5f, 1f)) +
        fadeOut(tween(220))

    // ── Переходы экранов ──────────────────────────────────────────────────────

    fun screenEnter(idx: Int, p: AnimParams = AnimParams()): EnterTransition {
        val d = tweenMs(360, p.screenTransSpeed)
        val e = easingFor(p.screenTransEasing)
        val spI = springForInt(p.screenTransDamping, p.screenTransStiffness)
        val spF = springForFloat(p.screenTransDamping, p.screenTransStiffness)
        return when (idx) {
            0  -> slideInHorizontally(tween(d, easing = e)) { (it * 0.35f).toInt() } + fadeIn(tween(tweenMs(280, p.screenTransSpeed)))
            1  -> slideInHorizontally(tween(d, easing = e)) { it } + fadeIn(tween(tweenMs(250, p.screenTransSpeed)))
            2  -> fadeIn(tween(tweenMs(380, p.screenTransSpeed), easing = e))
            3  -> scaleIn(spF, initialScale = 0.5f) + fadeIn(tween(tweenMs(280, p.screenTransSpeed)))
            4  -> scaleIn(tween(d, easing = e), initialScale = 1.5f) + fadeIn(tween(tweenMs(300, p.screenTransSpeed)))
            5  -> slideInVertically(tween(tweenMs(320, p.screenTransSpeed), easing = e)) { it }
            6  -> slideInHorizontally(tween(d, easing = e)) { it } + fadeIn(tween(tweenMs(200, p.screenTransSpeed)))
            7  -> slideInVertically(tween(d, easing = e)) { it } + fadeIn(tween(tweenMs(200, p.screenTransSpeed)))
            8  -> slideInHorizontally(spI) { (it * 0.4f).toInt() }
            9  -> slideInHorizontally(tween(d, easing = e)) { (it * 0.3f).toInt() } + scaleIn(tween(d), initialScale = 0.85f) + fadeIn(tween(tweenMs(280, p.screenTransSpeed)))
            10 -> scaleIn(tween(d, easing = e), initialScale = 0.0f, transformOrigin = TransformOrigin(0f, 0.5f)) + fadeIn(tween(tweenMs(300, p.screenTransSpeed)))
            11 -> scaleIn(tween(d, easing = e), initialScale = 1.3f) + fadeIn(tween(tweenMs(300, p.screenTransSpeed)))
            12 -> scaleIn(spF, initialScale = 0.85f) + fadeIn(tween(tweenMs(250, p.screenTransSpeed)))
            13 -> slideInHorizontally(tween(d, easing = e)) { it } + fadeIn(tween(tweenMs(280, p.screenTransSpeed)))
            14 -> slideInVertically(tween(d, easing = e)) { -it }
            15 -> slideInHorizontally(tween(d, easing = e)) { (it * 0.18f).toInt() } +
                  slideInVertically(tween(d, easing = e)) { (it * 0.08f).toInt() } +
                  fadeIn(tween(tweenMs(240, p.screenTransSpeed)))
            16 -> slideInHorizontally(tween(d, easing = e)) { (it * 0.12f).toInt() } +
                  scaleIn(tween(d, easing = e), initialScale = 0.94f) +
                  fadeIn(tween(tweenMs(240, p.screenTransSpeed)))
            17 -> slideInVertically(tween(d, easing = e)) { (it * 0.22f).toInt() } +
                  scaleIn(tween(d, easing = e), initialScale = 0.98f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                  fadeIn(tween(tweenMs(220, p.screenTransSpeed)))
            else -> AnimationRegistry.Screen.enter()
        }
    }

    fun screenExit(idx: Int, p: AnimParams = AnimParams()): ExitTransition {
        val d = tweenMs(300, p.screenTransSpeed)
        val e = easingFor(p.screenTransEasing)
        val spI = springForInt(p.screenTransDamping, p.screenTransStiffness)
        val spF = springForFloat(p.screenTransDamping, p.screenTransStiffness)
        return when (idx) {
            0  -> slideOutHorizontally(tween(d, easing = e)) { -(it * 0.12f).toInt() } + fadeOut(tween(tweenMs(220, p.screenTransSpeed)))
            1  -> slideOutHorizontally(tween(d, easing = e)) { -(it / 4) } + fadeOut(tween(tweenMs(200, p.screenTransSpeed)))
            2  -> fadeOut(tween(tweenMs(300, p.screenTransSpeed), easing = e))
            3  -> scaleOut(spF, targetScale = 0.5f) + fadeOut(tween(tweenMs(280, p.screenTransSpeed)))
            4  -> scaleOut(tween(d, easing = e), targetScale = 1.5f) + fadeOut(tween(tweenMs(300, p.screenTransSpeed)))
            5  -> slideOutVertically(tween(d, easing = e)) { -(it / 4) } + fadeOut(tween(tweenMs(200, p.screenTransSpeed)))
            6  -> slideOutHorizontally(tween(d, easing = e)) { -(it / 4) } + fadeOut(tween(tweenMs(200, p.screenTransSpeed)))
            7  -> slideOutVertically(tween(d, easing = e)) { -(it / 4) } + fadeOut(tween(tweenMs(200, p.screenTransSpeed)))
            8  -> slideOutHorizontally(spI) { -(it * 0.08f).toInt() }
            9  -> slideOutHorizontally(tween(d, easing = e)) { -(it * 0.08f).toInt() } + fadeOut(tween(tweenMs(200, p.screenTransSpeed)))
            10 -> scaleOut(tween(d, easing = e), targetScale = 0.9f, transformOrigin = TransformOrigin(0f, 0.5f)) + fadeOut(tween(tweenMs(220, p.screenTransSpeed)))
            11 -> scaleOut(tween(d, easing = e), targetScale = 1.3f) + fadeOut(tween(tweenMs(300, p.screenTransSpeed)))
            12 -> scaleOut(spF, targetScale = 0.9f) + fadeOut(tween(tweenMs(200, p.screenTransSpeed)))
            13 -> slideOutHorizontally(tween(d, easing = e)) { -(it / 4) } + fadeOut(tween(tweenMs(220, p.screenTransSpeed)))
            14 -> slideOutVertically(tween(d, easing = e)) { it / 4 } + fadeOut(tween(tweenMs(200, p.screenTransSpeed)))
            15 -> slideOutHorizontally(tween(d, easing = e)) { -(it * 0.08f).toInt() } +
                  slideOutVertically(tween(d, easing = e)) { -(it * 0.04f).toInt() } +
                  fadeOut(tween(tweenMs(220, p.screenTransSpeed)))
            16 -> slideOutHorizontally(tween(d, easing = e)) { -(it * 0.05f).toInt() } +
                  scaleOut(tween(d, easing = e), targetScale = 0.98f) +
                  fadeOut(tween(tweenMs(200, p.screenTransSpeed)))
            17 -> slideOutVertically(tween(d, easing = e)) { -(it * 0.08f).toInt() } +
                  scaleOut(tween(d, easing = e), targetScale = 0.98f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                  fadeOut(tween(tweenMs(180, p.screenTransSpeed)))
            else -> AnimationRegistry.Screen.exit()
        }
    }

    fun screenPopEnter(idx: Int, p: AnimParams = AnimParams()): EnterTransition {
        val d = tweenMs(360, p.screenTransSpeed)
        val e = easingFor(p.screenTransEasing)
        val spI = springForInt(p.screenTransDamping, p.screenTransStiffness)
        val spF = springForFloat(p.screenTransDamping, p.screenTransStiffness)
        return when (idx) {
            0  -> slideInHorizontally(tween(d, easing = e)) { -(it * 0.12f).toInt() } + fadeIn(tween(tweenMs(250, p.screenTransSpeed)))
            1  -> slideInHorizontally(tween(d, easing = e)) { -(it / 4) } + fadeIn(tween(tweenMs(250, p.screenTransSpeed)))
            2  -> fadeIn(tween(tweenMs(380, p.screenTransSpeed), easing = e))
            3  -> scaleIn(spF, initialScale = 0.5f) + fadeIn(tween(tweenMs(280, p.screenTransSpeed)))
            4  -> scaleIn(tween(d, easing = e), initialScale = 1.5f) + fadeIn(tween(tweenMs(300, p.screenTransSpeed)))
            5  -> slideInVertically(tween(tweenMs(320, p.screenTransSpeed), easing = e)) { -(it / 4) }
            6  -> slideInHorizontally(tween(d, easing = e)) { -(it / 4) } + fadeIn(tween(tweenMs(200, p.screenTransSpeed)))
            7  -> slideInVertically(tween(d, easing = e)) { -(it / 4) } + fadeIn(tween(tweenMs(200, p.screenTransSpeed)))
            8  -> slideInHorizontally(spI) { -(it * 0.08f).toInt() }
            9  -> slideInHorizontally(tween(d, easing = e)) { -(it * 0.08f).toInt() } +
                  scaleIn(tween(d, easing = e), initialScale = 0.85f) +
                  fadeIn(tween(tweenMs(250, p.screenTransSpeed)))
            10 -> scaleIn(tween(d, easing = e), initialScale = 0.9f, transformOrigin = TransformOrigin(0f, 0.5f)) +
                  fadeIn(tween(tweenMs(240, p.screenTransSpeed)))
            11 -> scaleIn(tween(d, easing = e), initialScale = 1.3f) +
                  fadeIn(tween(tweenMs(260, p.screenTransSpeed)))
            12 -> scaleIn(spF, initialScale = 0.85f) +
                  fadeIn(tween(tweenMs(220, p.screenTransSpeed)))
            13 -> slideInHorizontally(tween(d, easing = e)) { -(it / 4) } +
                  fadeIn(tween(tweenMs(220, p.screenTransSpeed)))
            14 -> slideInVertically(tween(d, easing = e)) { -(it / 4) } +
                  fadeIn(tween(tweenMs(180, p.screenTransSpeed)))
            15 -> slideInHorizontally(tween(d, easing = e)) { -(it * 0.08f).toInt() } +
                  slideInVertically(tween(d, easing = e)) { -(it * 0.04f).toInt() } +
                  fadeIn(tween(tweenMs(220, p.screenTransSpeed)))
            16 -> slideInHorizontally(tween(d, easing = e)) { -(it * 0.05f).toInt() } +
                  scaleIn(tween(d, easing = e), initialScale = 0.98f) +
                  fadeIn(tween(tweenMs(220, p.screenTransSpeed)))
            17 -> slideInVertically(tween(d, easing = e)) { -(it * 0.08f).toInt() } +
                  scaleIn(tween(d, easing = e), initialScale = 0.98f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                  fadeIn(tween(tweenMs(200, p.screenTransSpeed)))
            else -> AnimationRegistry.Screen.popEnter()
        }
    }

    fun screenPopExit(idx: Int, p: AnimParams = AnimParams()): ExitTransition {
        val d = tweenMs(300, p.screenTransSpeed)
        val e = easingFor(p.screenTransEasing)
        val spI = springForInt(p.screenTransDamping, p.screenTransStiffness)
        val spF = springForFloat(p.screenTransDamping, p.screenTransStiffness)
        return when (idx) {
            0  -> slideOutHorizontally(tween(d, easing = e)) { (it * 0.35f).toInt() } + fadeOut(tween(tweenMs(200, p.screenTransSpeed)))
            1  -> slideOutHorizontally(tween(d, easing = e)) { it } + fadeOut(tween(tweenMs(200, p.screenTransSpeed)))
            2  -> fadeOut(tween(tweenMs(300, p.screenTransSpeed), easing = e))
            3  -> scaleOut(spF, targetScale = 0.5f) + fadeOut(tween(tweenMs(280, p.screenTransSpeed)))
            4  -> scaleOut(tween(d, easing = e), targetScale = 1.5f) + fadeOut(tween(tweenMs(300, p.screenTransSpeed)))
            5  -> slideOutVertically(tween(d, easing = e)) { it } + fadeOut(tween(tweenMs(200, p.screenTransSpeed)))
            6  -> slideOutHorizontally(tween(d, easing = e)) { it } + fadeOut(tween(tweenMs(200, p.screenTransSpeed)))
            7  -> slideOutVertically(tween(d, easing = e)) { it } + fadeOut(tween(tweenMs(200, p.screenTransSpeed)))
            8  -> slideOutHorizontally(spI) { (it * 0.4f).toInt() }
            9  -> slideOutHorizontally(tween(d, easing = e)) { (it * 0.3f).toInt() } +
                  fadeOut(tween(tweenMs(200, p.screenTransSpeed)))
            10 -> scaleOut(tween(d, easing = e), targetScale = 0.9f, transformOrigin = TransformOrigin(0f, 0.5f)) +
                  fadeOut(tween(tweenMs(200, p.screenTransSpeed)))
            11 -> scaleOut(tween(d, easing = e), targetScale = 1.3f) +
                  fadeOut(tween(tweenMs(260, p.screenTransSpeed)))
            12 -> scaleOut(spF, targetScale = 0.85f) +
                  fadeOut(tween(tweenMs(200, p.screenTransSpeed)))
            13 -> slideOutHorizontally(tween(d, easing = e)) { it } +
                  fadeOut(tween(tweenMs(220, p.screenTransSpeed)))
            14 -> slideOutVertically(tween(d, easing = e)) { it / 3 } +
                  fadeOut(tween(tweenMs(180, p.screenTransSpeed)))
            15 -> slideOutHorizontally(tween(d, easing = e)) { (it * 0.18f).toInt() } +
                  slideOutVertically(tween(d, easing = e)) { (it * 0.08f).toInt() } +
                  fadeOut(tween(tweenMs(200, p.screenTransSpeed)))
            16 -> slideOutHorizontally(tween(d, easing = e)) { (it * 0.12f).toInt() } +
                  scaleOut(tween(d, easing = e), targetScale = 0.94f) +
                  fadeOut(tween(tweenMs(180, p.screenTransSpeed)))
            17 -> slideOutVertically(tween(d, easing = e)) { (it * 0.22f).toInt() } +
                  scaleOut(tween(d, easing = e), targetScale = 0.98f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                  fadeOut(tween(tweenMs(180, p.screenTransSpeed)))
            else -> AnimationRegistry.Screen.popExit()
        }
    }

    // ── Переключение вкладок ──────────────────────────────────────────────────

    fun tabEnterFromRight(idx: Int, p: AnimParams = AnimParams()): EnterTransition {
        val d = tweenMs(320, p.tabSwitchSpeed)
        val e = easingFor(p.tabSwitchEasing)
        val spI = springForInt(p.tabSwitchDamping, p.tabSwitchStiffness)
        val spF = springForFloat(p.tabSwitchDamping, p.tabSwitchStiffness)
        val follow = p.tabFollowThrough.coerceIn(0.6f, 1.8f)
        return when (idx) {
            0  -> AnimationRegistry.Screen.tabEnterFromRight()
            1  -> fadeIn(tween(tweenMs(280, p.tabSwitchSpeed), easing = e))
            2  -> scaleIn(spF, initialScale = 0.88f) + fadeIn(tween(tweenMs(250, p.tabSwitchSpeed)))
            3  -> slideInHorizontally(tween(d, easing = e)) { it } + fadeIn(tween(tweenMs(200, p.tabSwitchSpeed)))
            4  -> scaleIn(spF, initialScale = 0.7f) + slideInHorizontally(tween(d)) { (it * 0.3f).toInt() } + fadeIn(tween(tweenMs(280, p.tabSwitchSpeed)))
            5  -> slideInHorizontally(tween(d, easing = e)) { (it * 0.42f).toInt() } + fadeIn(tween(tweenMs(240, p.tabSwitchSpeed)))
            6  -> scaleIn(tween(d, easing = e), initialScale = 1f, transformOrigin = TransformOrigin(0.5f, 0f)) +
                  fadeIn(tween(tweenMs(220, p.tabSwitchSpeed)))
            7  -> scaleIn(tween(d, easing = e), initialScale = 1.12f) + fadeIn(tween(tweenMs(260, p.tabSwitchSpeed)))
            8  -> slideInHorizontally(tween(d, easing = e)) { (it * 0.18f * follow).toInt() } +
                  scaleIn(tween(d, easing = e), initialScale = 0.96f) +
                  fadeIn(tween(tweenMs(240, p.tabSwitchSpeed)))
            9  -> slideInHorizontally(tween(d, easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f))) { (it * 0.24f * follow).toInt() } +
                  fadeIn(tween(tweenMs(220, p.tabSwitchSpeed)))
            10 -> slideInHorizontally(spI) { (it * 0.32f * follow).toInt() } +
                  scaleIn(spF, initialScale = 0.92f) +
                  fadeIn(tween(tweenMs(240, p.tabSwitchSpeed)))
            11 -> scaleIn(tween(d, easing = e), initialScale = 1.06f) +
                  fadeIn(tween(tweenMs(260, p.tabSwitchSpeed)))
            else -> AnimationRegistry.Screen.tabEnterFromRight()
        }
    }

    fun tabExitToLeft(idx: Int, p: AnimParams = AnimParams()): ExitTransition {
        val d = tweenMs(280, p.tabSwitchSpeed)
        val e = easingFor(p.tabSwitchEasing)
        val spI = springForInt(p.tabSwitchDamping, p.tabSwitchStiffness)
        val spF = springForFloat(p.tabSwitchDamping, p.tabSwitchStiffness)
        val follow = p.tabFollowThrough.coerceIn(0.6f, 1.8f)
        return when (idx) {
            0  -> AnimationRegistry.Screen.tabExitToLeft()
            1  -> fadeOut(tween(tweenMs(220, p.tabSwitchSpeed), easing = e))
            2  -> scaleOut(spF, targetScale = 0.97f) + fadeOut(tween(tweenMs(200, p.tabSwitchSpeed)))
            3  -> slideOutHorizontally(tween(d, easing = e)) { -it } + fadeOut(tween(tweenMs(200, p.tabSwitchSpeed)))
            4  -> scaleOut(spF, targetScale = 0.8f) + slideOutHorizontally(tween(d)) { -(it * 0.3f).toInt() } + fadeOut(tween(tweenMs(220, p.tabSwitchSpeed)))
            5  -> slideOutHorizontally(tween(d, easing = e)) { -(it * 0.18f).toInt() } + fadeOut(tween(tweenMs(200, p.tabSwitchSpeed)))
            6  -> scaleOut(tween(d, easing = e), targetScale = 1f, transformOrigin = TransformOrigin(0.5f, 0f)) +
                  fadeOut(tween(tweenMs(200, p.tabSwitchSpeed)))
            7  -> scaleOut(tween(d, easing = e), targetScale = 0.92f) + fadeOut(tween(tweenMs(210, p.tabSwitchSpeed)))
            8  -> slideOutHorizontally(tween(d, easing = e)) { -(it * 0.10f * follow).toInt() } +
                  scaleOut(tween(d, easing = e), targetScale = 0.985f) +
                  fadeOut(tween(tweenMs(200, p.tabSwitchSpeed)))
            9  -> slideOutHorizontally(tween(d, easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f))) { -(it * 0.12f * follow).toInt() } +
                  fadeOut(tween(tweenMs(180, p.tabSwitchSpeed)))
            10 -> slideOutHorizontally(spI) { -(it * 0.18f * follow).toInt() } +
                  scaleOut(spF, targetScale = 0.95f) +
                  fadeOut(tween(tweenMs(200, p.tabSwitchSpeed)))
            11 -> scaleOut(tween(d, easing = e), targetScale = 1.02f) +
                  fadeOut(tween(tweenMs(220, p.tabSwitchSpeed)))
            else -> AnimationRegistry.Screen.tabExitToLeft()
        }
    }

    fun tabEnterFromLeft(idx: Int, p: AnimParams = AnimParams()): EnterTransition {
        val d = tweenMs(320, p.tabSwitchSpeed)
        val e = easingFor(p.tabSwitchEasing)
        val spI = springForInt(p.tabSwitchDamping, p.tabSwitchStiffness)
        val spF = springForFloat(p.tabSwitchDamping, p.tabSwitchStiffness)
        val follow = p.tabFollowThrough.coerceIn(0.6f, 1.8f)
        return when (idx) {
            0  -> AnimationRegistry.Screen.tabEnterFromLeft()
            1  -> fadeIn(tween(tweenMs(280, p.tabSwitchSpeed), easing = e))
            2  -> scaleIn(spF, initialScale = 0.88f) + fadeIn(tween(tweenMs(250, p.tabSwitchSpeed)))
            3  -> slideInHorizontally(tween(d, easing = e)) { -it } + fadeIn(tween(tweenMs(200, p.tabSwitchSpeed)))
            4  -> scaleIn(spF, initialScale = 0.7f) + slideInHorizontally(tween(d)) { -(it * 0.3f).toInt() } + fadeIn(tween(tweenMs(280, p.tabSwitchSpeed)))
            5  -> slideInHorizontally(tween(d, easing = e)) { -(it * 0.42f).toInt() } + fadeIn(tween(tweenMs(240, p.tabSwitchSpeed)))
            6  -> scaleIn(tween(d, easing = e), initialScale = 1f, transformOrigin = TransformOrigin(0.5f, 0f)) +
                  fadeIn(tween(tweenMs(220, p.tabSwitchSpeed)))
            7  -> scaleIn(tween(d, easing = e), initialScale = 1.12f) + fadeIn(tween(tweenMs(260, p.tabSwitchSpeed)))
            8  -> slideInHorizontally(tween(d, easing = e)) { -(it * 0.18f * follow).toInt() } +
                  scaleIn(tween(d, easing = e), initialScale = 0.96f) +
                  fadeIn(tween(tweenMs(240, p.tabSwitchSpeed)))
            9  -> slideInHorizontally(tween(d, easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f))) { -(it * 0.24f * follow).toInt() } +
                  fadeIn(tween(tweenMs(220, p.tabSwitchSpeed)))
            10 -> slideInHorizontally(spI) { -(it * 0.32f * follow).toInt() } +
                  scaleIn(spF, initialScale = 0.92f) +
                  fadeIn(tween(tweenMs(240, p.tabSwitchSpeed)))
            11 -> scaleIn(tween(d, easing = e), initialScale = 1.06f) +
                  fadeIn(tween(tweenMs(260, p.tabSwitchSpeed)))
            else -> AnimationRegistry.Screen.tabEnterFromLeft()
        }
    }

    fun tabExitToRight(idx: Int, p: AnimParams = AnimParams()): ExitTransition {
        val d = tweenMs(280, p.tabSwitchSpeed)
        val e = easingFor(p.tabSwitchEasing)
        val spI = springForInt(p.tabSwitchDamping, p.tabSwitchStiffness)
        val spF = springForFloat(p.tabSwitchDamping, p.tabSwitchStiffness)
        val follow = p.tabFollowThrough.coerceIn(0.6f, 1.8f)
        return when (idx) {
            0  -> AnimationRegistry.Screen.tabExitToRight()
            1  -> fadeOut(tween(tweenMs(220, p.tabSwitchSpeed), easing = e))
            2  -> scaleOut(spF, targetScale = 0.97f) + fadeOut(tween(tweenMs(200, p.tabSwitchSpeed)))
            3  -> slideOutHorizontally(tween(d, easing = e)) { it } + fadeOut(tween(tweenMs(200, p.tabSwitchSpeed)))
            4  -> scaleOut(spF, targetScale = 0.8f) + slideOutHorizontally(tween(d)) { (it * 0.3f).toInt() } + fadeOut(tween(tweenMs(220, p.tabSwitchSpeed)))
            5  -> slideOutHorizontally(tween(d, easing = e)) { (it * 0.18f).toInt() } + fadeOut(tween(tweenMs(200, p.tabSwitchSpeed)))
            6  -> scaleOut(tween(d, easing = e), targetScale = 1f, transformOrigin = TransformOrigin(0.5f, 0f)) +
                  fadeOut(tween(tweenMs(200, p.tabSwitchSpeed)))
            7  -> scaleOut(tween(d, easing = e), targetScale = 0.92f) + fadeOut(tween(tweenMs(210, p.tabSwitchSpeed)))
            8  -> slideOutHorizontally(tween(d, easing = e)) { (it * 0.10f * follow).toInt() } +
                  scaleOut(tween(d, easing = e), targetScale = 0.985f) +
                  fadeOut(tween(tweenMs(200, p.tabSwitchSpeed)))
            9  -> slideOutHorizontally(tween(d, easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f))) { (it * 0.12f * follow).toInt() } +
                  fadeOut(tween(tweenMs(180, p.tabSwitchSpeed)))
            10 -> slideOutHorizontally(spI) { (it * 0.18f * follow).toInt() } +
                  scaleOut(spF, targetScale = 0.95f) +
                  fadeOut(tween(tweenMs(200, p.tabSwitchSpeed)))
            11 -> scaleOut(tween(d, easing = e), targetScale = 1.02f) +
                  fadeOut(tween(tweenMs(220, p.tabSwitchSpeed)))
            else -> AnimationRegistry.Screen.tabExitToRight()
        }
    }

    // ── Мини-плеер ────────────────────────────────────────────────────────────

    fun miniPlayerEnter(idx: Int, p: AnimParams = AnimParams()): EnterTransition {
        val spI = springForInt(p.miniPlayerDamping, 0)
        val spF = springForFloat(p.miniPlayerDamping, 0)
        val fd = tween<Float>(tweenMs(300, p.miniPlayerSpeed), easing = easingFor(p.miniPlayerEasing))
        val floatiness = p.miniPlayerFloatiness.coerceIn(0.5f, 1.8f)
        return when (idx) {
            0  -> slideInVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) { it } + fadeIn(fd)
            1  -> slideInVertically(springForInt(1, 1)) { it } + fadeIn(fd)
            2  -> fadeIn(tween(tweenMs(320, p.miniPlayerSpeed), easing = easingFor(p.miniPlayerEasing)))
            3  -> scaleIn(spF, initialScale = 0.5f, transformOrigin = TransformOrigin(0.5f, 1f)) + fadeIn(fd)
            4  -> slideInVertically(spI) { it } + scaleIn(spF, initialScale = 0.8f)
            5  -> scaleIn(tween(tweenMs(280, p.miniPlayerSpeed), easing = easingFor(p.miniPlayerEasing)), initialScale = 1f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                  fadeIn(fd)
            6  -> slideInHorizontally(tween(tweenMs(300, p.miniPlayerSpeed), easing = easingFor(p.miniPlayerEasing))) { (it * 0.22f).toInt() } +
                  slideInVertically(tween(tweenMs(300, p.miniPlayerSpeed), easing = easingFor(p.miniPlayerEasing))) { it } +
                  fadeIn(fd)
            7  -> slideInVertically(tween(tweenMs(280, p.miniPlayerSpeed), easing = easingFor(p.miniPlayerEasing))) { (it * 0.55f * floatiness).toInt() } +
                  scaleIn(spF, initialScale = 0.92f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                  fadeIn(fd)
            8  -> slideInVertically(spI) { (it * 0.72f * floatiness).toInt() } +
                  slideInHorizontally(tween(tweenMs(260, p.miniPlayerSpeed), easing = easingFor(p.miniPlayerEasing))) { (it * 0.06f * floatiness).toInt() } +
                  fadeIn(fd)
            9  -> scaleIn(tween(tweenMs(300, p.miniPlayerSpeed), easing = easingFor(p.miniPlayerEasing)), initialScale = 1.05f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                  fadeIn(tween(tweenMs(320, p.miniPlayerSpeed), easing = easingFor(p.miniPlayerEasing)))
            else -> slideInVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) { it } + fadeIn(fd)
        }
    }

    fun miniPlayerExit(idx: Int, p: AnimParams = AnimParams()): ExitTransition {
        val d = tweenMs(250, p.miniPlayerSpeed)
        val e = easingFor(p.miniPlayerEasing)
        val floatiness = p.miniPlayerFloatiness.coerceIn(0.5f, 1.8f)
        return when (idx) {
            0  -> slideOutVertically(tween(d, easing = e)) { it } + fadeOut(tween(tweenMs(200, p.miniPlayerSpeed)))
            1  -> slideOutVertically(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)) { it } + fadeOut(tween(tweenMs(200, p.miniPlayerSpeed)))
            2  -> fadeOut(tween(tweenMs(250, p.miniPlayerSpeed), easing = e))
            3  -> scaleOut(tween(tweenMs(220, p.miniPlayerSpeed), easing = e), targetScale = 0.5f, transformOrigin = TransformOrigin(0.5f, 1f)) + fadeOut(tween(tweenMs(200, p.miniPlayerSpeed)))
            4  -> slideOutVertically(tween(d, easing = e)) { it } + scaleOut(tween(d), targetScale = 0.8f)
            5  -> scaleOut(tween(tweenMs(220, p.miniPlayerSpeed), easing = e), targetScale = 1f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                  fadeOut(tween(tweenMs(180, p.miniPlayerSpeed)))
            6  -> slideOutHorizontally(tween(tweenMs(260, p.miniPlayerSpeed), easing = e)) { (it * 0.12f).toInt() } +
                  slideOutVertically(tween(tweenMs(260, p.miniPlayerSpeed), easing = e)) { it } +
                  fadeOut(tween(tweenMs(180, p.miniPlayerSpeed)))
            7  -> slideOutVertically(tween(d, easing = e)) { (it * 0.45f * floatiness).toInt() } +
                  scaleOut(tween(d, easing = e), targetScale = 0.96f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                  fadeOut(tween(tweenMs(180, p.miniPlayerSpeed)))
            8  -> slideOutVertically(tween(d, easing = e)) { (it * 0.58f * floatiness).toInt() } +
                  slideOutHorizontally(tween(tweenMs(220, p.miniPlayerSpeed), easing = e)) { (it * 0.05f * floatiness).toInt() } +
                  fadeOut(tween(tweenMs(170, p.miniPlayerSpeed)))
            9  -> scaleOut(tween(tweenMs(240, p.miniPlayerSpeed), easing = e), targetScale = 1.03f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                  fadeOut(tween(tweenMs(220, p.miniPlayerSpeed)))
            else -> slideOutVertically(tween(d, easing = e)) { it } + fadeOut(tween(tweenMs(200, p.miniPlayerSpeed)))
        }
    }
}
