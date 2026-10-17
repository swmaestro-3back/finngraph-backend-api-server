package com.finngraph.auth.model

sealed interface RotationResult {

    data class Rotated(val userId: Long) : RotationResult
    data class ReuseDetected(val userId: Long) : RotationResult
    data object Unknown : RotationResult
}
