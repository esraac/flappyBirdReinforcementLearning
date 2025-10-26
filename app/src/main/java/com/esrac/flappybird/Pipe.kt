package com.esrac.flappybird

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.random.Random

class Pipe(context: Context, screenWidth: Int, screenHeight: Int) {
    var x: Float = screenWidth.toFloat()
    var width: Int = 150
    var screenHeight: Int = screenHeight

    var topPipeHeight: Float
    var bottomPipeY: Float

    private val pipeSpeed: Float = 8f

    companion object {
        const val GAP = 400 // Borular arasındaki boşluk
    }

    init {
        // Boruların yüksekliğini rastgele ayarla
        topPipeHeight = Random.nextInt(screenHeight / 4, screenHeight / 2).toFloat()
        bottomPipeY = topPipeHeight + GAP
    }

    fun update() {
        x -= pipeSpeed
    }

    fun draw(canvas: Canvas) {
        val paint = Paint().apply { color = Color.GREEN }

        // Üst boru
        canvas.drawRect(x, 0f, x + width, topPipeHeight, paint)

        // Alt boru
        canvas.drawRect(x, bottomPipeY, x + width, screenHeight.toFloat(), paint)
    }

    val isOffScreen: Boolean
        get() = x + width < 0
}