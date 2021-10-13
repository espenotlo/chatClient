module chatClient {
    requires javafx.fxml;
    requires javafx.controls;

    opens vi.ui to javafx.fxml;

    exports vi.ui;
}