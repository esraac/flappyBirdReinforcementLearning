package com.esrac.flappybird

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.random.Random

class GameScreen(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private var drawingThread: Thread? = null
    private var isRunning = false
    private val surfaceHolder: SurfaceHolder = holder

    // Oyun nesneleri
    private val bird = Bird(context)
    private val pipes = mutableListOf<Pipe>()
    private var score = 0
    private var gameOver = false

    // Reinforcement Learning için
    // DÜZELTME: Thread.State yerine kendi State veri sınıfımızı kullanıyoruz
    private val qTable = mutableMapOf<State, DoubleArray>() // Durum -> [Yukarı zıplama olasılığı, Hiçbir şey yapmama olasılığı]
    private val learningRate = 0.1
    private val discountFactor = 0.9
    private val epsilon = 0.1 // Keşif oranı

    // DÜZELTME: Thread.State yerine kendi State veri sınıfımızı kullanıyoruz
    private var lastState: State? = null
    private var lastAction: Int? = null // 0: Zıpla, 1: Hiçbir şey yapma

    init {
        surfaceHolder.addCallback(this)
        setFocusable(true)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isRunning = true
        drawingThread = Thread(this)
        drawingThread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Ekran boyutu değiştiğinde yapılacaklar (şu an için boş)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRunning = false
        try {
            drawingThread?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    override fun run() {
        while (isRunning) {
            if (!gameOver) {
                update()
            }
            draw()
            Thread.sleep(20) // Oyun hızını kontrol et
        }
    }

    private fun update() {
        val currentState = getState()
        val action = chooseAction(currentState)

        // Aksiyonu uygula
        if (action == 0) { // Zıpla
            bird.flap()
        }
        // Eğer aksiyon 1 ise (hiçbir şey yapma), kuş düşmeye devam eder

        bird.update()

        // Boruları güncelle
        val iterator = pipes.iterator()
        while (iterator.hasNext()) {
            val pipe = iterator.next()
            pipe.update()
            if (pipe.isOffScreen) {
                iterator.remove()
                score++ // Boruyu geçince puan kazan
            }

            // Çarpışma kontrolü
            if (bird.collidesWith(pipe)) {
                gameOver = true
                // Negatif ödül
                updateQTable(lastState, lastAction, -100.0, currentState)
                break
            }
        }

        // Yeni boru ekle
        if (pipes.isEmpty() || pipes.last().x < width / 2) {
            pipes.add(Pipe(context, width, height))
        }

        // Oyun alanı dışına çıkma kontrolü
        if (bird.y + bird.height > height || bird.y < 0) {
            gameOver = true
            // Negatif ödül
            updateQTable(lastState, lastAction, -100.0, currentState)
        }

        // Q-tablosunu güncelle
        val reward = if (gameOver) -100.0 else 1.0 // Geçici ödül: yaşamak için 1, ölmek için -100
        updateQTable(lastState, lastAction, reward, getState())

        lastState = currentState
        lastAction = action
    }

    private fun draw() {
        if (surfaceHolder.surface.isValid) {
            val canvas = surfaceHolder.lockCanvas()
            canvas.drawColor(Color.BLUE) // Arka plan
            bird.draw(canvas)
            pipes.forEach { it.draw(canvas) }

            // Skoru çiz
            val paint = Paint().apply {
                color = Color.WHITE
                textSize = 80f
            }
            canvas.drawText("Score: $score", 50f, 100f, paint)

            if (gameOver) {
                paint.color = Color.RED
                paint.textSize = 120f
                canvas.drawText("Game Over!", width / 2f - 250, height / 2f, paint)
                paint.textSize = 60f
                canvas.drawText("Restarting in 5s...", width / 2f - 200, height / 2f + 100, paint)

                // 5 saniye sonra oyunu yeniden başlat
                postDelayed({
                    resetGame()
                }, 5000)
            }
            surfaceHolder.unlockCanvasAndPost(canvas)
        }
    }

    // Reinforcement Learning metodları
    private fun getState(): State {
        // Kuşun konumu, en yakın borunun konumu ve boru boşluğunun konumu
        // RL için durumlarımızı bu şekilde basitleştirelim.
        val nextPipe = pipes.firstOrNull { it.x + it.width > bird.x }
        return if (nextPipe != null) {
            State(
                bird.y.toInt(),
                nextPipe.x.toInt(),
                nextPipe.topPipeHeight.toInt(),
                nextPipe.bottomPipeY.toInt()
            )
        } else {
            // Hiç boru yoksa veya çok uzaktaysa varsayılan bir durum
            State(bird.y.toInt(), width, height / 2, height / 2 + Pipe.GAP)
        }
    }

    private fun chooseAction(state: State): Int {
        val qValues = qTable.getOrPut(state) { DoubleArray(2) { 0.0 } } // 2 aksiyon: zıpla, hiçbir şey yapma

        // Epsilon-greedy stratejisi
        return if (Random.nextDouble() < epsilon) {
            Random.nextInt(2) // Keşfet (rastgele aksiyon)
        } else {
            // Sömür (en iyi aksiyon)
            qValues.indexOfMax()
        }
    }

    private fun updateQTable(
        oldState: State?,
        oldAction: Int?,
        reward: Double,
        newState: State
    ) {
        if (oldState == null || oldAction == null) return

        val oldQ = qTable.getOrPut(oldState) { DoubleArray(2) { 0.0 } }[oldAction]
        val newQMax = qTable.getOrPut(newState) { DoubleArray(2) { 0.0 } }.maxOrNull() ?: 0.0

        val newQ = oldQ + learningRate * (reward + discountFactor * newQMax - oldQ)
        qTable[oldState]?.set(oldAction, newQ)
    }

    private fun DoubleArray.indexOfMax(): Int {
        var maxVal = this[0]
        var maxIndex = 0
        for (i in 1 until size) {
            if (this[i] > maxVal) {
                maxVal = this[i]
                maxIndex = i
            }
        }
        return maxIndex
    }

    // Oyun yeniden başlatma
    private fun resetGame() {
        bird.reset(width, height)
        pipes.clear()
        score = 0
        gameOver = false
        lastState = null
        lastAction = null
    }

    // Kullanıcı dokunuşunu dinleme (şimdilik RL kontrol ettiği için dokunuşları pas geçebiliriz)
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // Bu örnekte RL ajanı kontrol ettiği için manuel dokunuşları pas geçebiliriz.
        // Eğer manuel kontrol eklemek isterseniz burada "bird.flap()" çağırabilirsiniz.
        return true
    }
}