package com.esrac.flappybird

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect


class Bird(context: Context) {
    var x: Float = 0f
    var y: Float = 0f
    var width: Int = 0
    var height: Int = 0
    private var velocityY: Float = 0f
    private val gravity: Float = 1.5f // Yer çekimi
    private val jumpStrength: Float = -25f // Zıplama gücü

    private val birdBitmap: Bitmap

    init {
        val originalBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bird)
        width = originalBitmap.width / 2 // Kuş resmini küçült
        height = originalBitmap.height / 2
        birdBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, false)
    }

    fun reset(screenWidth: Int, screenHeight: Int) {
        x = (screenWidth / 4).toFloat()
        y = (screenHeight / 2).toFloat()
        velocityY = 0f
    }

    fun update() {
        velocityY += gravity
        y += velocityY
    }

    fun flap() {
        velocityY = jumpStrength
    }

    fun draw(canvas: Canvas) {
        canvas.drawBitmap(birdBitmap, x, y, null)
    }

    fun collidesWith(pipe: Pipe): Boolean {
        val birdRect = Rect(x.toInt(), y.toInt(), (x + width).toInt(), (y + height).toInt())
        val topPipeRect = Rect(
            pipe.x.toInt(), 0, (pipe.x + pipe.width).toInt(), pipe.topPipeHeight.toInt()
        )
        val bottomPipeRect = Rect(
            pipe.x.toInt(), pipe.bottomPipeY.toInt(), (pipe.x + pipe.width).toInt(), pipe.screenHeight
        )
        return birdRect.intersect(topPipeRect) || birdRect.intersect(bottomPipeRect)
    }
}