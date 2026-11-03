package com.sanoob.caro

/** Simple heuristic opponent: scores each empty cell by attack and defense value. */
object CaroAi {
    fun bestMove(game: CaroGame, me: Int): Pair<Int, Int> {
        val n = game.size
        if (game.moveCount == 0) return n / 2 to n / 2
        val opp = CaroGame.other(me)
        var best = n / 2 to n / 2
        var bestScore = Long.MIN_VALUE
        for (r in 0 until n) for (c in 0 until n) {
            if (game.get(r, c) != CaroGame.EMPTY || !hasNeighbor(game, r, c)) continue
            val attack = cellScore(game, r, c, me)
            val defense = cellScore(game, r, c, opp)
            // Small bonus for staying near the center breaks ties sensibly.
            val center = (n - Math.abs(r - n / 2) - Math.abs(c - n / 2)).toLong()
            val score = attack * 11 / 10 + defense + center
            if (score > bestScore) {
                bestScore = score
                best = r to c
            }
        }
        return best
    }

    private fun hasNeighbor(g: CaroGame, r: Int, c: Int): Boolean {
        for (dr in -2..2) for (dc in -2..2) {
            val rr = r + dr
            val cc = c + dc
            if (g.inside(rr, cc) && g.get(rr, cc) != CaroGame.EMPTY) return true
        }
        return false
    }

    private fun cellScore(g: CaroGame, r: Int, c: Int, p: Int): Long {
        var total = 0L
        for (d in CaroGame.DIRS) {
            var count = 1
            var openEnds = 0
            for (sign in intArrayOf(1, -1)) {
                var rr = r + d[0] * sign
                var cc = c + d[1] * sign
                while (g.inside(rr, cc) && g.get(rr, cc) == p) {
                    count++
                    rr += d[0] * sign
                    cc += d[1] * sign
                }
                if (g.inside(rr, cc) && g.get(rr, cc) == CaroGame.EMPTY) openEnds++
            }
            total += when {
                count >= 5 -> 10_000_000
                count == 4 && openEnds == 2 -> 1_000_000
                count == 4 && openEnds == 1 -> 100_000
                count == 3 && openEnds == 2 -> 50_000
                count == 3 && openEnds == 1 -> 1_000
                count == 2 && openEnds == 2 -> 500
                count == 2 && openEnds == 1 -> 50
                count == 1 && openEnds == 2 -> 10
                else -> 0
            }
        }
        return total
    }
}
