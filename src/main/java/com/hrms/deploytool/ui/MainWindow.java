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

    private LandingPage landingPage;
    private ValidationModal validationModal;
    private WorkspacePage workspacePage;

    private java.io.File selectedZip;
    private java.io.File extractedFolder;
    private com.hrms.deploytool.util.ZipUtil.ZipStats zipStats;

    public void setSelectedZip(java.io.File selectedZip) { this.selectedZip = selectedZip; }
    public java.io.File getSelectedZip() { return selectedZip; }
    
    public void setExtractedFolder(java.io.File extractedFolder) { this.extractedFolder = extractedFolder; }
    public java.io.File getExtractedFolder() { return extractedFolder; }

    public void setZipStats(com.hrms.deploytool.util.ZipUtil.ZipStats stats) {
        this.zipStats = stats;
        this.extractedFolder = (stats != null) ? stats.extractedDir : null;
    }
    public com.hrms.deploytool.util.ZipUtil.ZipStats getZipStats() { return zipStats; }

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
        if (validationModal != null) validationModal.stopFlow();
        landingPage = new LandingPage(this);
        root.getChildren().setAll(landingPage.getNode());
    }

    /**
     * Overlays the validation mock modal over the landing page.
     * @param failMode True to mock a corrupted archive, False for a successful check.
     */
    public void showValidation(boolean failMode) {
        validationModal = new ValidationModal(this);
        root.getChildren().setAll(landingPage.getNode(), validationModal.getNode());
        validationModal.startFlow(failMode);
    }

    /** Displays the workspace planning page. */
    public void showWorkspace() {
        if (validationModal != null) validationModal.stopFlow();
        workspacePage = new WorkspacePage(this);
        root.getChildren().setAll(workspacePage.getNode());
    }
}
