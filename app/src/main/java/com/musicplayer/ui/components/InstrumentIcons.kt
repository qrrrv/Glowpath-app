package com.musicplayer.ui.components

import com.musicplayer.R

/** Возвращает ресурс иконки инструмента на основе ID трека (12 вариантов) */
fun instrumentIconRes(songId: Long): Int {
    val idx = (songId % 12).toInt().let { if (it < 0) it + 12 else it }
    return when (idx) {
        0  -> R.drawable.drum
        1  -> R.drawable.acoustic_guitar
        2  -> R.drawable.clasic_piano
        3  -> R.drawable.banjo
        4  -> R.drawable.accordion
        5  -> R.drawable.bongos
        6  -> R.drawable.conga
        7  -> R.drawable.electronic_sound
        8  -> R.drawable.harmonica
        9  -> R.drawable.maracas
        10 -> R.drawable.metal_guitar
        else -> R.drawable.metal_guitar_2
    }
}
