package com.esrac.flappybird

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.max
import kotlin.math.abs
import kotlin.math.min

// JSON serileştirme için gerekli konfigürasyon
private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

@Serializable
class QLearningAgent(
    // Durum boyutları için radikal basitleştirme
    val verticalDistanceBins: Int = 10, // Kuşun boru açıklığına dikey uzaklığı
    val velBins: Int = 5,              // Kuşun dikey hızı

    // Diğer binleri kaldırdık veya 1 yaptık (etkisiz hale getirdik)
    val dummyBins1: Int = 1, // Yerine geçici dummy bins
    val dummyBins2: Int = 1, // Yerine geçici dummy bins

    val actionSize: Int = 2, // 0: Hiçbir şey yapma, 1: Zıpla

    val alpha: Double = 0.2,
    val gamma: Double = 0.99,
    var epsilon: Double = 1.0
) {
    // Toplam durum sayısı bu parametrelerin çarpımı ile belirlenir
    // Sadece verticalDistanceBins ve velBins'i kullanıyoruz.
    val stateSize: Int = verticalDistanceBins * velBins

    // Q-tablosu oluşturulur
    val qTable: Array<DoubleArray> = Array(stateSize) { DoubleArray(actionSize) { 0.0 } }

    fun chooseAction(state: Int): Int {
        if (state < 0 || state >= stateSize) {
            return (0 until actionSize).random()
        }
        return if (Math.random() < epsilon) {
            (0 until actionSize).random()
        } else {
            qTable[state].indices.maxByOrNull { qTable[state][it] } ?: 0
        }
    }

    fun learn(state: Int, action: Int, reward: Double, nextState: Int, isTerminal: Boolean = false) {
        if (state < 0 || state >= stateSize || nextState < 0 || nextState >= stateSize) {
            println("WARNING: learn called with invalid state/nextState. State: $state, NextState: $nextState (Range: 0-${stateSize-1})")
            return
        }
        if (action < 0 || action >= actionSize) {
            println("WARNING: learn called with invalid action. Action: $action")
            return
        }

        val predict = qTable[state][action]
        val maxFutureQ = if (isTerminal) 0.0 else (qTable[nextState].maxOrNull() ?: 0.0)
        val target = reward + gamma * maxFutureQ
        qTable[state][action] = predict + alpha * (target - predict)
    }

    fun updateEpsilon(episode: Int, totalEpisodes: Int, minEpsilon: Double = 0.01) {
        epsilon = max(minEpsilon, 1.0 - (episode.toDouble() / totalEpisodes))
    }

    fun saveToFile(filePath: String) {
        try {
            val jsonString = json.encodeToString(qTable)
            File(filePath).writeText(jsonString)
            println("Q-Table başarıyla kaydedildi: $filePath")
        } catch (e: Exception) {
            println("Q-Table kaydedilirken hata oluştu: ${e.localizedMessage}")
        }
    }

    fun loadFromFile(filePath: String) {
        val file = File(filePath)
        if (file.exists()) {
            try {
                val jsonString = file.readText()
                val loadedTable = json.decodeFromString<Array<DoubleArray>>(jsonString)

                if (loadedTable.size == qTable.size && loadedTable.all { it.size == actionSize }) {
                    for (i in loadedTable.indices) {
                        for (j in loadedTable[i].indices) {
                            qTable[i][j] = loadedTable[i][j]
                        }
                    }
                    println("Q-Table başarıyla yüklendi: $filePath")
                } else {
                    println("UYARI: Yüklenen Q-Table boyutu mevcut yapı ile uyuşmuyor. Yeni bir öğrenme başlayabilir. Beklenen durum: ${qTable.size}, Yüklenen durum: ${loadedTable.size}. Beklenen aksiyon: $actionSize, Yüklenen aksiyon: ${loadedTable.firstOrNull()?.size ?: 0}")
                }
            } catch (e: Exception) {
                println("Q-Table yüklenirken hata oluştu: ${e.localizedMessage}")
            }
        } else {
            println("Q-Table dosyası bulunamadı: $filePath. Yeni bir öğrenme başlayacak.")
        }
    }

    /**
     * Durum temsilini radikal bir şekilde basitleştiriyoruz:
     * Sadece kuşun boru açıklığının orta noktasına olan dikey uzaklığı ve dikey hızı.
     * Bu, ajanın "ne zaman zıplamalıyım" sorusuna odaklanmasını sağlar.
     */
    fun getState(
        birdY: Int,
        birdVelocity: Int,
        pipeTopY: Int,
        pipeBottomY: Int,
        screenHeight: Int
        // screenWidth artık kullanılmıyor
    ): Int {
        // 1. Kuşun Dikey Hızı (velBins)
        val birdVelIndex = when {
            birdVelocity < -15 -> 0 // Çok hızlı yukarı
            birdVelocity < 0 -> 1   // Yavaş yukarı
            birdVelocity == 0 -> 2  // Neredeyse sabit
            birdVelocity < 15 -> 3  // Yavaş aşağı
            else -> 4               // Çok hızlı aşağı
        }.coerceIn(0, velBins - 1)

        // 2. Kuşun boru açıklığının orta noktasına olan dikey uzaklığı (verticalDistanceBins)
        val pipeGapMidY = (pipeTopY + pipeBottomY) / 2
        val verticalDistanceToMid = birdY - pipeGapMidY // Negatifse kuş açıklığın altında, pozitifse üstünde

        // verticalDistanceToMid değerini ayrıklaştırma.
        // Örneğin, -screenHeight/2'den +screenHeight/2'ye kadar değer alabilir.
        // Bunu verticalDistanceBins'e sığdırın.
        val verticalRange = screenHeight / 2 // Ekranın yarısı kadar bir aralıkta değer alabilir
        val verticalDistanceIndex = ((verticalDistanceToMid.toDouble() / verticalRange) * (verticalDistanceBins / 2) + (verticalDistanceBins / 2)).toInt().coerceIn(0, verticalDistanceBins - 1)

        // Sadece iki bin kullanarak durumu oluştur
        return verticalDistanceIndex * velBins + birdVelIndex
    }

    /**
     * Ödül fonksiyonu da basitleştirildi.
     */
    fun getReward(
        passedPipe: Boolean,
        hitObstacle: Boolean
    ): Double {
        if (hitObstacle) {
            return -1000.0 // Ölüm cezası
        }
        if (passedPipe) {
            return +1000.0 // Boru geçme ödülü (daha büyük yaptık)
        }
        // Her adımda küçük bir pozitif ödül vermek, ajanı hayatta kalmaya teşvik eder.
        // Boruya çarpmadıkça her adımda küçük bir ödül alacak.
        return +1.0
    }
}