package com.example.connectfour

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.connectfour.engine.BOARD_HEIGHT
import com.example.connectfour.engine.BOARD_WIDTH
import com.example.connectfour.engine.ConnectFourEngine
import com.example.connectfour.engine.GameAlgorithm
import com.example.connectfour.engine.MinimaxAlgorithm
import com.example.connectfour.engine.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How many plies the computer looks ahead. Bump up for a stronger (slower) opponent. */
private const val SEARCH_DEPTH = 5

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConnectFourScreen()
                }
            }
        }
    }
}

private fun colorName(player: Int): String = if (player == Player.PLAYER1) "yellow" else "red"

/**
 * Lets [rememberSaveable] survive configuration changes (e.g. screen rotation)
 * without needing ConnectFourEngine itself to be Parcelable: we just save the
 * sequence of columns played and replay them into a fresh engine on restore.
 */
private val ConnectFourEngineSaver = listSaver<ConnectFourEngine, Int>(
    save = { engine -> engine.moveHistory() },
    restore = { columns ->
        ConnectFourEngine().apply {
            var player = Player.PLAYER1
            for (column in columns) {
                doPlayerMove(player, column)
                player = otherPlayer(player)
            }
        }
    }
)

@Composable
fun ConnectFourScreen(algorithm: GameAlgorithm = remember { MinimaxAlgorithm() }) {
    val engine = rememberSaveable(saver = ConnectFourEngineSaver) { ConnectFourEngine() }
    var boardVersion by rememberSaveable { mutableIntStateOf(0) }
    var computerThinking by rememberSaveable { mutableStateOf(false) }

    // Which player number (PLAYER1/PLAYER2) the human is currently playing as.
    // Piece colors stay fixed to the player number that placed them - this only
    // controls who plays which color from here on.
    var humanPlayer by rememberSaveable { mutableIntStateOf(Player.PLAYER1) }
    val computerPlayer = engine.otherPlayer(humanPlayer)

    // These read engine.movesPlayed fresh every call, instead of a `val` snapshot -
    // important because they're also called from inside the LaunchedEffect coroutine
    // below, after engine.movesPlayed has changed mid-effect.
    fun turnPlayer(): Int = if (engine.movesPlayed % 2 == 0) Player.PLAYER1 else Player.PLAYER2
    fun isHumanTurn(): Boolean = !engine.gameOver() && turnPlayer() == humanPlayer

    fun currentStatus(): String = when {
        engine.gameWon(humanPlayer) -> "You win!"
        engine.gameWon(computerPlayer) -> "Computer wins!"
        engine.gameDrawn() -> "It's a draw!"
        turnPlayer() == humanPlayer -> "Your move"
        else -> "Computer is thinking..."
    }

    var statusText by rememberSaveable { mutableStateOf(currentStatus()) }

    // Whenever the board changes (or sides get switched) and it's the computer's turn, let it play.
    LaunchedEffect(boardVersion, humanPlayer) {
        if (!engine.gameOver() && turnPlayer() == computerPlayer) {
            computerThinking = true
            statusText = currentStatus()
            val column = withContext(Dispatchers.Default) {
                algorithm.bestColumn(engine, computerPlayer, SEARCH_DEPTH)
            }
            engine.doPlayerMove(computerPlayer, column)
            computerThinking = false
            statusText = currentStatus()
            boardVersion++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Connect Four", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "You: ${colorName(humanPlayer)}   •   Computer: ${colorName(computerPlayer)}",
            fontSize = 14.sp
        )
        Spacer(Modifier.height(16.dp))
        Text(statusText, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))

        Board(
            engine = engine,
            boardVersion = boardVersion,
            enabled = !computerThinking && isHumanTurn(),
            onColumnClick = { column ->
                if (!engine.columnFull(column)) {
                    engine.doPlayerMove(humanPlayer, column)
                    statusText = currentStatus()
                    boardVersion++
                }
            }
        )

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            for (col in 0 until BOARD_WIDTH) {
                Box(
                    modifier = Modifier.size(width = 44.dp, height = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (col + 1).toString(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                engine.reset()
                humanPlayer = Player.PLAYER1
                statusText = "Your move"
                boardVersion++
            }) {
                Text("New Game")
            }
            OutlinedButton(
                enabled = !computerThinking && isHumanTurn(),
                onClick = {
                    // Swap who controls which color. The computer then plays this turn.
                    humanPlayer = computerPlayer
                }
            ) {
                Text("Switch Sides")
            }
        }
    }
}

@Composable
private fun Board(
    engine: ConnectFourEngine,
    boardVersion: Int,
    enabled: Boolean,
    onColumnClick: (Int) -> Unit
) {
    // Reading boardVersion here ties this composable's recomposition to move changes,
    // even though the actual values below are read straight from the mutable engine.
    @Suppress("UNUSED_EXPRESSION") boardVersion

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1565C0))
            .padding(8.dp)
    ) {
        for (row in BOARD_HEIGHT - 1 downTo 0) {
            Row {
                for (col in 0 until BOARD_WIDTH) {
                    val value = engine.counterValue(col, row)
                    val cellColor = when (value) {
                        Player.PLAYER1 -> Color(0xFFFFC107)
                        Player.PLAYER2 -> Color(0xFFE53935)
                        else -> Color(0xFFFFFFFF)
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(cellColor)
                            .clickable(enabled = enabled) { onColumnClick(col) }
                    )
                }
            }
        }
    }
}
