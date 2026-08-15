package com.example.connectfour.engine

import kotlin.random.Random

/**
 * Direct port of CConnectFourState::BestColumn / Evaluate. Depth-limited
 * minimax with a fail-soft alpha/beta-style cutoff, using [ConnectFourEngine.gameScore]
 * as the static evaluation at the search horizon.
 */
class MinimaxAlgorithm(private val random: Random = Random.Default) : GameAlgorithm {

    override fun bestColumn(engine: ConnectFourEngine, player: Int, searchDepth: Int): Int {
        var equalMoves = 1
        var bestScore = -1000
        var bestColumn = -1

        for (column in engine.searchOrder) {
            if (engine.countersInColumn(column) == BOARD_HEIGHT) continue

            engine.doMove(player, column)
            if (bestColumn == -1) bestColumn = column

            if (engine.gameWon(player)) {
                engine.undoMove(column)
                return column
            }

            val score = evaluate(engine, player, searchDepth, -1000, -bestScore)
            engine.undoMove(column)

            if (score > bestScore) {
                bestScore = score
                bestColumn = column
                equalMoves = 1
            }
            if (score == bestScore) {
                equalMoves++
                if (random.nextInt(equalMoves) == 0) {
                    bestColumn = column
                }
            }
        }

        return bestColumn
    }

    private fun evaluate(engine: ConnectFourEngine, aPlayer: Int, depth: Int, lowerBoundIn: Int, upperBoundIn: Int): Int {
        val player = engine.otherPlayer(aPlayer)
        if (depth == 1) return engine.gameScore(aPlayer)

        var bestScore = -1000
        var lowerBound = -upperBoundIn
        val upperBound = -lowerBoundIn

        for (column in engine.searchOrder) {
            if (engine.countersInColumn(column) == BOARD_HEIGHT) continue

            engine.doMove(player, column)
            if (engine.gameWon(player)) {
                engine.undoMove(column)
                return -990 - depth
            }

            val score = evaluate(engine, player, depth - 1, lowerBound, upperBound)
            engine.undoMove(column)

            if (score > bestScore) bestScore = score
            if (score > lowerBound) lowerBound = score
            if (score > upperBound) break
        }

        return -bestScore
    }
}
