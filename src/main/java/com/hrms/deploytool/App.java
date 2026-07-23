package com.hrms.deploytool;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.hrms.deploytool.ui.MainWindow;

/**
 * Main JavaFX Application entry point for the HRMS Deploy Tool.
 */
public class App extends Application {

    private MainWindow window;

    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Initializes the JavaFX stage, applies the global CSS stylesheet,
     * and displays the main window root container.
     */
    @Override
    public void start(Stage stage) {
        window = new MainWindow(stage);
        Scene scene = new Scene(window.getRoot(), 980, 720);
        scene.getStylesheets().add(
            getClass().getResource("/com/hrms/deploytool/styles.css").toExternalForm()
        );
        stage.setTitle("HRMS Deploy Tool — v1.0.0");
        stage.setMinWidth(860);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Cleans up temporary extraction directories when the application closes.
     */
    @Override
    public void stop() {
        if (window != null) {
            window.cleanup();
        }
    }
}
