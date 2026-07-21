package com.hrms.deploytool.ui;

import javafx.scene.Parent;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/** 
 * Root navigation controller.
 * Maintains the primary StackPane layout and handles swapping the visible page
 * (Landing vs. Workspace) as well as overlaying the Validation Modal.
 */
public class MainWindow {

    private final Stage stage;
    private final StackPane root = new StackPane();

    private final LandingPage  landingPage;
    private final ValidationModal validationModal;
    private final WorkspacePage workspacePage;

    /**
     * Constructs the main window and initializes all UI pages.
     * @param stage The primary JavaFX stage.
     */
    public MainWindow(Stage stage) {
        this.stage = stage;
        landingPage      = new LandingPage(this);
        validationModal  = new ValidationModal(this);
        workspacePage    = new WorkspacePage(this);

        root.getStyleClass().add("bg-black");
        showLanding();
    }

    /** @return The base container holding the active pages. */
    public Parent getRoot() { return root; }

    /** Displays the initial landing dashboard page. */
    public void showLanding() {
        validationModal.stopFlow();
        root.getChildren().setAll(landingPage.getNode());
    }

    /**
     * Overlays the validation mock modal over the landing page.
     * @param failMode True to mock a corrupted archive, False for a successful check.
     */
    public void showValidation(boolean failMode) {
        // Keep landing behind, overlay modal
        root.getChildren().setAll(landingPage.getNode(), validationModal.getNode());
        validationModal.startFlow(failMode);
    }

    /** Displays the workspace planning page. */
    public void showWorkspace() {
        validationModal.stopFlow();
        root.getChildren().setAll(workspacePage.getNode());
    }
}
