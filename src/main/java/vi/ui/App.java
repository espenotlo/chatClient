package vi.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {

    private static Scene scene;

    @Override
    public void start(Stage stage) throws IOException {
        scene = new Scene(loadFxml("login"));
        stage.setScene(scene);
        stage.setTitle("Chat Client");
        stage.show();
    }

    /**
     * Returns a parent node of a .fxml.
     * @param fxml the name of the .fxml file to be loaded.
     * @return {@code Parent} the loaded fxml.
     * @throws IOException exception if fxml could not be loaded.
     */
    private static Parent loadFxml(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
    }

    /**
     * Sets the scene root to a given fxml.
     * @param fxml {@code String} name of the .fxml file.
     * @throws IOException if unable to load fxml.
     */
    static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFxml(fxml));
    }

    /**
     * Sets the size of the stage.
     * @param width width of the stage
     * @param height height of the stage
     */
    static void setSize(int width, int height) {
        scene.getWindow().setWidth(width);
        scene.getWindow().setHeight(height);
        scene.getWindow().centerOnScreen();
    }

    /**
     * Launches the application.
     * @param args arguments
     */
    static void main(String[] args) {
        launch();
    }

    @Override
    public void stop()  {
        }
    }

