module chatClient {
    requires javafx.controls;
    requires javafx.fxml;

    opens no.ntnu.datakomm.chat to javafx.fxml;
    exports no.ntnu.datakomm.chat;
}