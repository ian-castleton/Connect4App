package com.example.connectfour.engine

/**
 * Strategy interface for choosing a move.
 *
 * [ConnectFourEngine] only knows the rules of the game (board, legal moves,
 * win detection, line scoring). Anything that decides WHICH move to make -
 * minimax, a neural net, a random player, etc. - implements this interface
 * instead of living inside the engine. To add a new algorithm later, just
 * write a new class implementing [GameAlgorithm] and swap it in.
 */
interface GameAlgorithm {

    /**
     * Chooses a column for [player] to drop a piece into, given the current
     * state of [engine]. [searchDepth] is a hint for algorithms that do
     * lookahead (e.g. minimax ply count); implementations that don't need
     * it are free to ignore it.
     *
     * Implementations must leave [engine] unchanged when they return - any
     * moves made for lookahead must be undone before returning.
     */
    fun bestColumn(engine: ConnectFourEngine, player: Int, searchDepth: Int): Int
}
