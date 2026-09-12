package com.musicplayer.ui.navigation

/**
 * Хелперы навигационных переходов.
 * Делегируют к [AnimationRegistry.Screen] — единому реестру анимаций.
 */

const val TRANSITION_DURATION = AnimationRegistry.Durations.SCREEN_TRANSITION_MS

fun enterTransition()    = AnimationRegistry.Screen.enter()
fun exitTransition()     = AnimationRegistry.Screen.exit()
fun popEnterTransition() = AnimationRegistry.Screen.popEnter()
fun popExitTransition()  = AnimationRegistry.Screen.popExit()
