package net.zomis.minesweeper.analyze

enum class SimplifyResult {
    FAILED_NEGATIVE_RESULT, FAILED_TOO_BIG_RESULT, NO_EFFECT, SIMPLIFIED;

    val isFailure: Boolean
        get() = this == FAILED_NEGATIVE_RESULT || this == FAILED_TOO_BIG_RESULT
}