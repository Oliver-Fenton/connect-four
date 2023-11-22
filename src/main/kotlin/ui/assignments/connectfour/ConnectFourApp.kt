package ui.assignments.connectfour

import javafx.application.Application
import javafx.scene.Scene
import javafx.stage.Stage
import ui.assignments.connectfour.model.Model
import ui.assignments.connectfour.ui.View

class ConnectFourApp : Application() {
    override fun start(stage: Stage) {
        val model = Model
        val view = View(stage, model)

        stage.minWidth = 600.0
        stage.minHeight = 600.0

        view.start()
    }
}