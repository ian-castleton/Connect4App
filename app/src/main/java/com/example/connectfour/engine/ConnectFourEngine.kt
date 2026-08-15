package com.example.connectfour.engine

/**
 * Pure game-rules engine for Connect Four. This is a direct port of the
 * board/line bookkeeping from the original CConnectFourState (Connect4state.cpp)
 * and its Python translation.
 *
 * Deliberately contains NO move-choosing logic (no minimax, no evaluation
 * heuristics beyond the raw [gameScore] line-counting) - that lives in
 * classes implementing [GameAlgorithm], so a different algorithm (e.g. a
 * neural net) can be dropped in later without touching board/rules code.
 */

const val BOARD_WIDTH = 7
const val BOARD_HEIGHT = 6
const val WINNING_LINES = 69

private const val ONE_IN_LINE = 2
private const val TWO_IN_LINE = 4
private const val THREE_IN_LINE = 8
private const val FOUR_IN_LINE = 16

object Player {
    const val EMPTY = 0
    const val PLAYER1 = 1
    const val PLAYER2 = 2
}

enum class GameState {
    IN_PROGRESS, DRAWN, WON_BY_PLAYER1, WON_BY_PLAYER2
}

/** One of the 69 possible four-in-a-row lines on the board. */
private class WinningLine {
    // score[countersOfPlayer1][countersOfPlayer2] -> heuristic value from Player1's perspective
    private val score = Array(5) { IntArray(5) }
    val counters = intArrayOf(0, 0)
    val points = arrayOfNulls<Pair<Int, Int>>(4)

    init {
        score[1][0] = ONE_IN_LINE
        score[2][0] = TWO_IN_LINE
        score[3][0] = THREE_IN_LINE
        score[4][0] = FOUR_IN_LINE
        score[0][1] = -ONE_IN_LINE
        score[0][2] = -TWO_IN_LINE
        score[0][3] = -THREE_IN_LINE
        score[0][4] = -FOUR_IN_LINE
    }

    fun lineFull(): Boolean = counters[0] == 4 || counters[1] == 4
    fun lineScore(): Int = score[counters[0]][counters[1]]
    fun increment(player: Int) { counters[player - 1]++ }
    fun decrement(player: Int) { counters[player - 1]-- }
    fun reset() { counters[0] = 0; counters[1] = 0 }
}

private class BoardSquare {
    var value = Player.EMPTY
    val winningLines = mutableListOf<Int>()
}

class ConnectFourEngine {

    private val board = Array(BOARD_WIDTH) { Array(BOARD_HEIGHT) { BoardSquare() } }
    private val winningLines = Array(WINNING_LINES) { WinningLine() }
    private val countersInColumn = IntArray(BOARD_WIDTH)
    private val history = IntArray(43)  // History is indexed from position 1.

    /** Column visit order used by search algorithms - centre-out, as in the original. */
    val searchOrder = intArrayOf(3, 2, 4, 1, 5, 0, 6)

    var movesPlayed: Int = 0
        private set

    var gameState: GameState = GameState.IN_PROGRESS
        private set

    init {
        initializeWinningLines()
    }

    private fun setLine(index: Int, p0: Pair<Int, Int>, p1: Pair<Int, Int>, p2: Pair<Int, Int>, p3: Pair<Int, Int>) {
        val line = winningLines[index]
        line.points[0] = p0
        line.points[1] = p1
        line.points[2] = p2
        line.points[3] = p3
        board[p0.first][p0.second].winningLines.add(index)
        board[p1.first][p1.second].winningLines.add(index)
        board[p2.first][p2.second].winningLines.add(index)
        board[p3.first][p3.second].winningLines.add(index)
    }

    private fun initializeWinningLines() {
        var n = 0

        // Horizontal
        for (i in 0 until 4) for (j in 0 until 6) {
            setLine(n, i to j, (i + 1) to j, (i + 2) to j, (i + 3) to j)
            n++
        }

        // Vertical
        for (i in 0 until 7) for (j in 0 until 3) {
            setLine(n, i to j, i to (j + 1), i to (j + 2), i to (j + 3))
            n++
        }

        // Diagonal, bottom-left to top-right
        for (i in 0 until 4) for (j in 0 until 3) {
            setLine(n, i to j, (i + 1) to (j + 1), (i + 2) to (j + 2), (i + 3) to (j + 3))
            n++
        }

        // Diagonal, bottom-right to top-left
        for (i in 3 until 7) for (j in 0 until 3) {
            setLine(n, i to j, (i - 1) to (j + 1), (i - 2) to (j + 2), (i - 3) to (j + 3))
            n++
        }
    }

    fun countersInColumn(column: Int): Int = countersInColumn[column]

    fun columnFull(column: Int): Boolean = countersInColumn[column] >= BOARD_HEIGHT

    fun counterValue(column: Int, row: Int): Int = board[column][row].value

    fun otherPlayer(player: Int): Int = if (player == Player.PLAYER1) Player.PLAYER2 else Player.PLAYER1

    fun gameWon(player: Int): Boolean = when (player) {
        Player.PLAYER1 -> gameState == GameState.WON_BY_PLAYER1
        Player.PLAYER2 -> gameState == GameState.WON_BY_PLAYER2
        else -> false
    }

    fun gameDrawn(): Boolean = gameState == GameState.DRAWN

    fun gameOver(): Boolean = gameState != GameState.IN_PROGRESS

    /** Sum of every line's heuristic score, from [player]'s point of view. */
    fun gameScore(player: Int): Int {
        var total = 0
        for (line in winningLines) total += line.lineScore()
        return if (player == Player.PLAYER1) total else -total
    }

    /** Drops [player]'s piece into [column] without bookkeeping move history - used by search. */
    fun doMove(player: Int, column: Int) {
        val j = countersInColumn[column]
        board[column][j].value = player
        countersInColumn[column]++

        for (lineIdx in board[column][j].winningLines) {
            val line = winningLines[lineIdx]
            line.increment(player)
            if (line.lineFull()) {
                gameState = if (player == Player.PLAYER1) GameState.WON_BY_PLAYER1 else GameState.WON_BY_PLAYER2
            }
        }
    }

    /** Reverses the most recent [doMove] call for this column. Returns the row it was removed from. */
    fun undoMove(column: Int): Int {
        countersInColumn[column]--
        val j = countersInColumn[column]
        val player = board[column][j].value
        board[column][j].value = Player.EMPTY
        gameState = GameState.IN_PROGRESS

        for (lineIdx in board[column][j].winningLines) {
            winningLines[lineIdx].decrement(player)
        }
        return j
    }

    /** Plays a real move for the current game (tracks history so it can be undone / replayed). */
    fun doPlayerMove(player: Int, column: Int) {
        doMove(player, column)
        movesPlayed++
        if (movesPlayed == 42 && gameState == GameState.IN_PROGRESS) {
            gameState = GameState.DRAWN
        }
        history[movesPlayed] = column
    }

    fun undoPlayerMove() {
        if (movesPlayed <= 0) return
        undoMove(history[movesPlayed])
        movesPlayed--
    }

    /**
     * The column played on each ply so far, in order. Players always alternate
     * starting with [Player.PLAYER1], so replaying these columns with
     * [doPlayerMove] (alternating player each time) reconstructs an identical
     * engine state. Useful for saving/restoring the game, e.g. across
     * configuration changes.
     */
    fun moveHistory(): List<Int> = (1..movesPlayed).map { history[it] }

    fun reset() {
        movesPlayed = 0
        gameState = GameState.IN_PROGRESS
        for (col in board) for (square in col) square.value = Player.EMPTY
        for (line in winningLines) line.reset()
        for (i in countersInColumn.indices) countersInColumn[i] = 0
    }
}
