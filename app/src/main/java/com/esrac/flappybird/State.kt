package com.esrac.flappybird

// Durum, kuşun ve en yakın borunun konum bilgilerini içerir.
data class State(
    val birdY: Int,
    val nextPipeX: Int,
    val nextPipeTopY: Int,
    val nextPipeBottomY: Int
)