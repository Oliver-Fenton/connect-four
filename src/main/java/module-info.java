module ui.assignments.connectfour {
    requires javafx.controls;
    requires javafx.fxml;
    requires kotlin.stdlib;


    opens ui.assignments.connectfour to javafx.fxml;
    exports ui.assignments.connectfour;
}