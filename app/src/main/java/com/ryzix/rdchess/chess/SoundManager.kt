package com.ryzix.rdchess.chess

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class SoundManager(context: Context) {
    private val soundPool: SoundPool
    private val sounds = mutableMapOf<String, Int>()

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()

        loadSound(context, "move", "sounds/move.mp3")
        loadSound(context, "capture", "sounds/capture.mp3")
        loadSound(context, "confirmation", "sounds/confirmation.mp3")
        loadSound(context, "error", "sounds/error.mp3")
    }

    private fun loadSound(context: Context, key: String, asset: String) {
        try {
            val afd = context.assets.openFd(asset)
            val id = soundPool.load(afd, 1)
            sounds[key] = id
        } catch (e: Exception) {
            // asset not found, skip
        }
    }

    fun play(key: String) {
        sounds[key]?.let { soundPool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    fun release() {
        soundPool.release()
    }
}
