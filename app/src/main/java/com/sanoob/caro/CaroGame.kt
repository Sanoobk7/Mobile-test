package com.sanoob.caro

/** Gomoku / Caro rules: first player to get 5 in a row (any direction) wins. */
class CaroGame(val size: Int = 15) {
    companion object {
        const val EMPTY = 0
        const val X = 1
        const val O = 2
        val DIRS = arrayOf(intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(1, -1))
        fun other(p: Int) = if (p == X) O else X
    }

    val cells = IntArray(size * size)
    var current = X
        private set
    var winner = EMPTY
        private set
    var winLine: List<Pair<Int, Int>> = emptyList()
        private set
    var lastMove: Pair<Int, Int>? = null
        private set
    var moveCount = 0
        private set

    val isOver get() = winner != EMPTY || moveCount == size * size
    val isDraw get() = winner == EMPTY && moveCount == size * size

    fun get(r: Int, c: Int) = cells[r * size + c]
    fun inside(r: Int, c: Int) = r in 0 until size && c in 0 until size

    fun reset(starter: Int = X) {
        cells.fill(EMPTY)
        current = starter
        winner = EMPTY
        winLine = emptyList()
        lastMove = null
        moveCount = 0
    }

    /** Places a stone for the current player. Returns false if the move is illegal. */
    fun play(r: Int, c: Int): Boolean {
        if (isOver || !inside(r, c) || get(r, c) != EMPTY) return false
        cells[r * size + c] = current
        moveCount++
        lastMove = r to c
        checkWin(r, c)
        if (winner == EMPTY) current = other(current)
        return true
    }

    private fun checkWin(r: Int, c: Int) {
        val p = get(r, c)
        for (d in DIRS) {
            val line = mutableListOf(r to c)
            for (sign in intArrayOf(1, -1)) {
                var rr = r + d[0] * sign
                var cc = c + d[1] * sign
                while (inside(rr, cc) && get(rr, cc) == p) {
                    line.add(rr to cc)
                    rr += d[0] * sign
                    cc += d[1] * sign
                }
            }
            if (line.size >= 5) {
                winner = p
                winLine = line
                return
            }
        }
    }
}
