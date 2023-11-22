package ui.assignments.connectfour.ui

import javafx.animation.Interpolator
import javafx.animation.TranslateTransition
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.scene.text.Font
import javafx.scene.text.FontPosture
import javafx.scene.text.FontWeight
import javafx.stage.Stage
import javafx.util.Duration
import ui.assignments.connectfour.model.Model
import ui.assignments.connectfour.model.Piece
import ui.assignments.connectfour.model.Player
import java.nio.channels.FileLock
import kotlin.RuntimeException
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

private const val GRID_WIDTH = 400.5
private const val GRID_HEIGHT = 350.5
private const val GRID_NUM_COLUMNS = 8
private const val GRID_NUM_ROWS = 7
private const val PIECE_DIAMETER = 40.5
private const val GRID_PNG_PATH = "/ui/assignments/connectfour/grid_8x7.png"
private const val PIECE_RED_PNG_PATH = "/ui/assignments/connectfour/piece_red.png"
private const val PIECE_YELLOW_PNG_PATH = "/ui/assignments/connectfour/piece_yellow.png"
private const val PLAYER_COLUMN_WIDTH = 150.0
private const val PLAYER_AREA_HEIGHT = 50.0
private const val CONTROL_AREA_HEIGHT = 125.0
private const val GAME_AREA_HEIGHT = CONTROL_AREA_HEIGHT + GRID_HEIGHT
private const val WINDOW_WIDTH = 500.0
private const val WINDOW_HEIGHT = 500.0

class View constructor(private val stage: Stage, private val model: Model) {
    private val gridURL =  javaClass.getResource(GRID_PNG_PATH)?.toString() ?: throw IllegalArgumentException("Image file not found")
    private val player1PieceURL =  javaClass.getResource(PIECE_RED_PNG_PATH)?.toString() ?: throw IllegalArgumentException("Image file not found")
    private val player2PieceURL =  javaClass.getResource(PIECE_YELLOW_PNG_PATH)?.toString() ?: throw IllegalArgumentException("Image file not found")

    private var winWidth = WINDOW_WIDTH
    private var winHeight = WINDOW_HEIGHT
    init {
        model.onNextPlayer.addListener { _ ->
            // spawn current player's piece
            if (model.onNextPlayer.value != Player.NONE) gameArea.spawnPiece(model.onNextPlayer.value)
        }
        model.onPieceDropped.addListener { _, _, new ->
            if (new != null) {
                gameArea.dropPiece(new)

            } else { // unsuccessful drop
                gameArea.resetPiece()
            }
        }
        model.onGameWin.addListener { _, _, new ->
            // stop game & display winning player message
            if (new != Player.NONE) {
                gameArea.displayMessage(new)
                players.score.update()
            }
        }
        model.onGameDraw.addListener { _ ->
            // stop game & display draw message
            gameArea.displayMessage(Player.NONE)
        }
    }

    private val players = object: HBox() {
        val player1 = Label("Player #1").apply {
            minWidth = PLAYER_COLUMN_WIDTH
            font = Font.font("Courier New", FontWeight.BOLD, FontPosture.REGULAR, 24.0)
            textFill = Color.GRAY
            setMargin(this, Insets(10.0))
        }
        val player2 = Label("Player #2").apply {
            minWidth = PLAYER_COLUMN_WIDTH
            font = Font.font("Courier New", FontWeight.BOLD, FontPosture.REGULAR, 24.0)
            textFill = Color.GRAY
            setMargin(this, Insets(10.0))
        }
        val score = object: Label("0 - 0") {
            init {
                maxWidth = Double.MAX_VALUE
                alignment = Pos.CENTER
                font = Font.font("Courier New", FontWeight.BOLD, FontPosture.REGULAR, 24.0)
                textFill = Color.GRAY
                setMargin(this, Insets(10.0))
                setHgrow(this, Priority.ALWAYS)
            }
            fun update() {
                text = "${model.p1Score} - ${model.p2Score}"
            }
        }
        init {
            prefWidth = WINDOW_WIDTH
            prefHeight = PLAYER_AREA_HEIGHT
            children.addAll(player1, score, player2)

            stage.widthProperty().addListener { _, _, new ->
                val newVal: Double = new as Double
                score.prefWidth = (newVal - (PLAYER_COLUMN_WIDTH * 2))
            }
        }
    }

    private data class DragInfo(
        var target: ImageView? = null,
        var anchorX: Double = 0.0,
        var anchorY: Double = 0.0,
        var initialX: Double = 0.0,
        var initialY: Double = 0.0
    )

    private val gamePieceDragFilters = object {
        var dragInfo = DragInfo()
        val mousePressedFilter: EventHandler<MouseEvent> = EventHandler<MouseEvent> {
            dragInfo = DragInfo(
                gameArea.activePiece, it.sceneX, it.sceneY,
                gameArea.activePiece.translateX, gameArea.activePiece.translateY
            )
        }
        val mouseDraggedFilter: EventHandler<MouseEvent> = EventHandler<MouseEvent> {
            val newX = dragInfo.initialX + it.sceneX - dragInfo.anchorX
            val newY = dragInfo.initialY + it.sceneY - dragInfo.anchorY
            if (newX > 0 && newX + gameArea.activePiece.fitWidth < gameArea.layoutBounds.width) gameArea.activePiece.translateX = snapToColumn(newX)
            if (newY > 0 && newY + gameArea.activePiece.fitHeight < ((stage.height - PLAYER_AREA_HEIGHT) * 0.20) - 10.0) gameArea.activePiece.translateY = newY
        }
        val mouseReleasedFilter: EventHandler<MouseEvent> = EventHandler<MouseEvent> {
            dragInfo = DragInfo()
            getColumn(gameArea.activePiece.translateX)?.let { column ->
                model.dropPiece(column)
            }
        }

        fun snapToColumn(x: Double): Double {
            val offset = (stage.width * 0.125) - (gameArea.activePiece.fitWidth / 2)
            val columnWidth = gameArea.grid.fitWidth / GRID_NUM_COLUMNS
            val xOffset = (stage.width * 0.125) + (columnWidth - gameArea.activePiece.fitWidth) / 2
            if (x >= offset && x < offset + gameArea.grid.fitWidth) return xOffset + ((floor((x - offset) / columnWidth)) * columnWidth)
            return x
        }
        fun getColumn(x: Double): Int? {
            val offset = (stage.width * 0.125) - (gameArea.activePiece.fitWidth / 2)
            val columnWidth = gameArea.grid.fitWidth / GRID_NUM_COLUMNS
            if (x >= offset && x < offset + gameArea.grid.fitWidth) return floor((x - offset) / columnWidth).toInt()
            return null
        }
    }

    private inner class GamePiece constructor(val player: Player): ImageView() {
        var piece: Piece? = null
        init {
            image = when (player) {
                Player.ONE -> Image(player1PieceURL)
                Player.TWO -> Image(player2PieceURL)
                Player.NONE -> throw RuntimeException("cannot create game piece for Player.NONE!")
            }
            isVisible = false
            fitWidth = PIECE_DIAMETER * (stage.width / WINDOW_WIDTH)
            fitHeight = PIECE_DIAMETER * (stage.height / WINDOW_HEIGHT)
            addEventFilter(MouseEvent.MOUSE_PRESSED, gamePieceDragFilters.mousePressedFilter)
            addEventFilter(MouseEvent.MOUSE_DRAGGED, gamePieceDragFilters.mouseDraggedFilter)
            addEventFilter(MouseEvent.MOUSE_RELEASED, gamePieceDragFilters.mouseReleasedFilter)

            stage.widthProperty().addListener { _, old, new ->
                val columnWidth = gameArea.grid.fitWidth / GRID_NUM_COLUMNS
                val xOffset = (stage.width * 0.125) + ((columnWidth - gameArea.activePiece.fitWidth) / 2)
                val newVal: Double = new as Double
                val oldVal: Double = old as Double
                winWidth = newVal
                val scale: Double = newVal / WINDOW_WIDTH
                fitWidth = PIECE_DIAMETER * scale
                val curX = translateX / oldVal
                translateX = (piece?.x?.times(columnWidth)?.plus(xOffset)) ?: (newVal * curX)
            }
            stage.heightProperty().addListener { _, old, new ->
                val rowHeight = gameArea.grid.fitHeight / GRID_NUM_ROWS
                val yOffset = ((stage.height - PLAYER_AREA_HEIGHT) * 0.20) - 10.0 + ((rowHeight - gameArea.activePiece.fitHeight) / 2)
                val newVal: Double = new as Double
                val oldVal: Double = old as Double
                winHeight = newVal
                val scale: Double = newVal / WINDOW_HEIGHT
                fitHeight = PIECE_DIAMETER * scale
                val curY = translateY / oldVal
                translateY = (piece?.y?.times(rowHeight)?.plus(yOffset)) ?: (newVal * curY)
            }
        }

        fun reset() {
            val x: Double = when (player) {
                Player.ONE -> ((stage.width * 0.10) / 2) - (gameArea.activePiece.fitWidth / 2)
                Player.TWO -> ((stage.width * 0.10) * 1.5) + gameArea.grid.fitWidth - (gameArea.activePiece.fitWidth / 2)
                Player.NONE -> throw RuntimeException("Error: cannot reset Player.NONE's piece")
            }
            // define duration to be relative to the piece's position so that the movement speed is similar for all animations
            val duration = Duration.millis((abs(x - translateX) / GRID_WIDTH) * 1000.0)
            val animation = TranslateTransition(duration, this).apply {
                fromX = translateX
                fromY = translateY
                toX = x
                toY = (((stage.height - PLAYER_AREA_HEIGHT) * 0.2) / 2) - (gameArea.activePiece.fitWidth / 2)
                interpolator = Interpolator.EASE_IN
            }
            animation.play()
        }

        fun drop(piece: Piece) {
            this.piece = piece
            val rowHeight = gameArea.grid.fitHeight / GRID_NUM_ROWS
            val yOffset = ((stage.height - PLAYER_AREA_HEIGHT) * 0.20) - 10.0 + ((rowHeight - gameArea.activePiece.fitHeight) / 2)
            val y = yOffset + (rowHeight * piece.y) //- gameArea.curPiece.translateY
            // define duration to be relative to the piece's position so that the movement speed is similar for all animations
            val duration = Duration.millis((abs(y - translateY) / gameArea.grid.fitHeight) * 1000.0)
            val animation = TranslateTransition(duration, this).apply {
                fromX = translateX
                fromY = translateY
                toX = translateX
                toY = y
                interpolator = Interpolator.EASE_IN
            }
            animation.play()
            // make piece non-interactive
            removeEventFilter(MouseEvent.MOUSE_PRESSED, gamePieceDragFilters.mousePressedFilter)
            removeEventFilter(MouseEvent.MOUSE_DRAGGED, gamePieceDragFilters.mouseDraggedFilter)
            removeEventFilter(MouseEvent.MOUSE_RELEASED, gamePieceDragFilters.mouseReleasedFilter)
        }
    }

    private val gameArea = object: StackPane() {
        val grid = ImageView(gridURL).apply {
            fitWidth = stage.width * 0.75
            fitHeight = (stage.height - PLAYER_AREA_HEIGHT) * 0.75
            translateX = (WINDOW_WIDTH / 2) - (GRID_WIDTH / 2)
            translateY = ((stage.height - PLAYER_AREA_HEIGHT) * 0.20) - 10.0
            stage.widthProperty().addListener { _, _, new ->
                val newVal: Double = new as Double
                fitWidth = newVal * 0.75
                translateX = (newVal / 2) - (fitWidth / 2)
            }
            stage.heightProperty().addListener { _, _, new ->
                val newVal: Double = new as Double
                fitHeight = (newVal - PLAYER_AREA_HEIGHT) * 0.75
                translateY = ((newVal - PLAYER_AREA_HEIGHT) * 0.20) - 10.0
            }
        }
        var activePiece: GamePiece = GamePiece(Player.ONE)
        val pieces = StackPane(Rectangle(WINDOW_WIDTH, CONTROL_AREA_HEIGHT).apply {fill = Color.TRANSPARENT}).apply {
            alignment = Pos.TOP_LEFT
        }
        init {
            alignment = Pos.TOP_LEFT
            children.addAll(pieces, grid)

            stage.widthProperty().addListener { _, _, new ->
                val newVal: Double = new as Double
                width = newVal
            }
            stage.heightProperty().addListener { _, _, new ->
                val newVal: Double = new as Double
                height = newVal
            }
        }

        fun displayMessage(player: Player?) {
            val msg = when(player) {
                Player.ONE -> "Player #1 Wins!"
                Player.TWO -> "Player #2 Wins!"
                Player.NONE -> "It's a Draw!"
                null -> "Click here to start game!"
            }
            val text = VBox().apply {
                alignment = Pos.CENTER
                children.add(Label(msg).apply {
                    font = Font.font("Courier New", FontWeight.BOLD, FontPosture.REGULAR, min(stage.width / 800.0, stage.height / 400.0) * 24.0)
                    alignment = Pos.CENTER
                })
                if (player != null) {
                    children.add(Label("Click here to restart!").apply {
                        font = Font.font("Courier New", FontWeight.BOLD, FontPosture.REGULAR, min(stage.width / 800.0, stage.height / 400.0) * 16.0)
                        alignment = Pos.CENTER
                    })
                }
            }
            val message = Button("", text).apply {
                prefWidth = stage.width / 2
                prefHeight = (stage.height - PLAYER_AREA_HEIGHT) * 0.15
                translateX = stage.width * 0.25
                alignment = Pos.CENTER
                background = Background(BackgroundFill(
                    when(player) {Player.ONE -> Color.RED; Player.TWO -> Color.YELLOW; Player.NONE -> Color.ORANGE; null -> Color.LAWNGREEN},
                    CornerRadii.EMPTY, Insets.EMPTY
                ))
                if (player == null) onMouseClicked = EventHandler { model.startGame(); isVisible = false }
                else onMouseClicked = EventHandler { restartGame(); isVisible = false }
                stage.widthProperty().addListener { _, _, new ->
                    val newVal: Double = new as Double
                    prefWidth = newVal / 2
                    translateX = newVal * 0.25
                    (text.children[0] as Label).font = Font.font("Courier New", FontWeight.BOLD, FontPosture.REGULAR, min(stage.width / 800.0, stage.height / 400.0) * 24.0)
                    if (text.children.size > 1) (text.children[1] as Label).font = Font.font("Courier New", FontWeight.BOLD, FontPosture.REGULAR, min(stage.width / 800.0, stage.height / 400.0) * 16.0)
                }
                stage.heightProperty().addListener { _, _, new ->
                    val newVal: Double = new as Double
                    prefHeight = (newVal - PLAYER_AREA_HEIGHT) * 0.15
                }
            }
            children.add(message)
        }

        fun spawnPiece(player: Player) {
            when (player) {
                Player.ONE -> {
                    activePiece = GamePiece(Player.ONE)
                    activePiece.translateX = (stage.width * 0.10) - (activePiece.fitWidth)
                }
                Player.TWO -> {
                    activePiece = GamePiece(Player.TWO)
                    activePiece.translateX = (stage.width * 0.90)
                }
                Player.NONE -> throw RuntimeException("cannot spawn piece for Player.NONE")
            }
            pieces.children.add(activePiece)
            activePiece.translateY += (CONTROL_AREA_HEIGHT / 2) - (PIECE_DIAMETER / 2)
            activePiece.isVisible = true
        }

        fun resetPiece() {
            activePiece.reset()
        }

        fun dropPiece(piece: Piece) {
            activePiece.drop(piece)
        }

        fun clearBoard() {
            val animation = TranslateTransition(Duration(1500.0), pieces).apply {
                byY = GAME_AREA_HEIGHT
                interpolator = Interpolator.EASE_IN
            }
            animation.play()
            animation.onFinished = EventHandler {
                pieces.children.clear()
                pieces.translateY = 0.0 // reset window position after animation
                model.restartGame()
            }
        }
        // an attempt at procedurally generating a grid, didn't quite work but I may come back to this.
        /*fun drawGrid(numColumns: Int, numRows: Int): Shape {
            fun drawCell(column: Int, row: Int): Shape {
                val square = Rectangle((column * 100.0), (row * 100.0), 100.0, 100.0).apply {
                    fill = Color.CYAN
                    border = Border(BorderStroke(Color.RED, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT))
                }
                val cutout = Circle((column * 100.0) + 50.0, (row * 100.0) + 50.0, 40.0, Color.TRANSPARENT).apply {
                    border = Border(BorderStroke(Color.RED, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT))
                }
                return Shape.subtract(square, cutout)
            }
            fun drawRow(numColumns: Int, rowNum: Int): Shape {
                var row = drawCell(0, rowNum)
                for (i in 1 until numColumns) {
                    row = Shape.union(row, drawCell(i, rowNum))
                }
                return row
            }

            var grid: Shape = drawRow(numColumns, 0)
            for (i in 1 until numRows) {
                grid = Shape.union(grid, drawRow(numColumns, i))
            }
            grid.fill = Color.CYAN

            return grid
        }*/
    }

    private val root = VBox(players, gameArea).apply {
        prefWidth = WINDOW_WIDTH
        prefHeight = WINDOW_HEIGHT
        alignment = Pos.TOP_CENTER
    }

    init {
        stage.title = "CS349 - A3 Connect Four - obfenton"
        stage.scene = Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT)
        //stage.isResizable = false
    }

    fun start() {
        stage.show()
        gameArea.displayMessage(null) // display start message
    }

    fun restartGame() {
        gameArea.clearBoard()

    }
}