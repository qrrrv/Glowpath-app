package com.musicplayer.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Высота нижней панели, которая рисуется поверх контента:
 * мини-плеер + навигация + системный inset (жесты/кнопки).
 *
 * Экраны корневых вкладок используют её как нижний contentPadding у списков,
 * чтобы последний элемент можно было прокрутить выше мини-плеера
 * (как «дотягивание» списка в конце). Значение считает MainActivity по
 * реальному размеру панели, поэтому оно корректно и на телефонах, и на планшетах.
 *
 * Значение по умолчанию (120.dp) — прежнее поведение, если провайдера нет.
 */
val LocalBottomChromeHeight = compositionLocalOf { 120.dp }

/** Небольшой дополнительный зазор между последним элементом и панелью. */
val BottomChromeExtraSpace = 16.dp
