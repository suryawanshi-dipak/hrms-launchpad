package com.hrms.deploytool.ui;

import com.hrms.deploytool.archive.ZipExtractor;
import com.hrms.deploytool.archive.ZipValidator;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

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

    private File selectedZip;
    private ZipValidator.ValidationResult validationResult;
    private ZipExtractor.ExtractionResult extractionResult;

    private String sessionPassphrase = "";
    private boolean connectionVerified = false;

    /** Tracks temp directories for cleanup on exit. */
    private final List<File> tempDirsToCleanup = new ArrayList<>();

    public void setSelectedZip(File selectedZip) { this.selectedZip = selectedZip; }
    public File getSelectedZip() { return selectedZip; }
    
    public void setValidationResult(ZipValidator.ValidationResult result) { this.validationResult = result; }
    public ZipValidator.ValidationResult getValidationResult() { return validationResult; }

    public void setSessionPassphrase(String passphrase) { this.sessionPassphrase = passphrase; }
    public String getSessionPassphrase() { return sessionPassphrase; }

    public void setConnectionVerified(boolean verified) { this.connectionVerified = verified; }
    public boolean isConnectionVerified() { return connectionVerified; }

    public void setExtractionResult(ZipExtractor.ExtractionResult result) {
        this.extractionResult = result;
        if (result != null && result.rawTempDir() != null) {
            tempDirsToCleanup.add(result.rawTempDir());
        }
    }
    public ZipExtractor.ExtractionResult getExtractionResult() { return extractionResult; }

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

    /** @return The primary JavaFX stage. */
    public Stage getStage() { return stage; }

    /** Displays the initial landing dashboard page. */
    public void showLanding() {
        if (validationModal != null) validationModal.stopFlow();
        landingPage = new LandingPage(this);
        root.getChildren().setAll(landingPage.getNode());
    }

    /**
     * Overlays the validation modal over the landing page.
     * Validation results are determined by the actual zip file content,
     * not a demo toggle.
     */
    public void showValidation() {
        validationModal = new ValidationModal(this);
        root.getChildren().setAll(landingPage.getNode(), validationModal.getNode());
        validationModal.startFlow();
    }

    /** Displays the workspace planning page. */
    public void showWorkspace() {
        if (validationModal != null) validationModal.stopFlow();
        workspacePage = new WorkspacePage(this);
        root.getChildren().setAll(workspacePage.getNode());
    }

    /** Displays a dialog to configure SMTP settings. */
    public void showSettingsDialog() {
        Dialog<Properties> dialog = new Dialog<>();
        dialog.setTitle("Settings — HRMS Deploy Tool");
        dialog.setHeaderText("Configure Email Notification (SMTP) Settings");
        dialog.initOwner(stage);

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 20));

        TextField smtpHost = new TextField();
        smtpHost.setPromptText("smtp.gmail.com");
        TextField smtpPort = new TextField();
        smtpPort.setPromptText("587");
        TextField smtpUser = new TextField();
        smtpUser.setPromptText("user@gmail.com");
        PasswordField smtpPass = new PasswordField();
        smtpPass.setPromptText("password");
        TextField emailFrom = new TextField();
        emailFrom.setPromptText("noreply@hrms.com");
        TextField emailTo = new TextField();
        emailTo.setPromptText("devs@hrms.com, ops@hrms.com");

        // Load existing config
        Properties config = com.hrms.deploytool.deploy.ConfigManager.loadConfig();
        smtpHost.setText(config.getProperty("smtpHost", ""));
        smtpPort.setText(config.getProperty("smtpPort", "587"));
        smtpUser.setText(config.getProperty("smtpUser", ""));
        smtpPass.setText(config.getProperty("smtpPass", ""));
        emailFrom.setText(config.getProperty("emailFrom", ""));
        emailTo.setText(config.getProperty("emailTo", ""));

        grid.add(new Label("SMTP Host:"), 0, 0);
        grid.add(smtpHost, 1, 0);
        grid.add(new Label("SMTP Port:"), 0, 1);
        grid.add(smtpPort, 1, 1);
        grid.add(new Label("SMTP Username:"), 0, 2);
        grid.add(smtpUser, 1, 2);
        grid.add(new Label("SMTP Password:"), 0, 3);
        grid.add(smtpPass, 1, 3);
        grid.add(new Label("Sender (From):"), 0, 4);
        grid.add(emailFrom, 1, 4);
        grid.add(new Label("Recipients (To):"), 0, 5);
        grid.add(emailTo, 1, 5);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                config.setProperty("smtpHost", smtpHost.getText());
                config.setProperty("smtpPort", smtpPort.getText());
                config.setProperty("smtpUser", smtpUser.getText());
                config.setProperty("smtpPass", smtpPass.getText());
                config.setProperty("emailFrom", emailFrom.getText());
                config.setProperty("emailTo", emailTo.getText());
                return config;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(props -> {
            com.hrms.deploytool.deploy.ConfigManager.saveConfig(props);
            Alert success = new Alert(Alert.AlertType.INFORMATION);
            success.setTitle("Success");
            success.setHeaderText(null);
            success.setContentText("SMTP settings saved successfully.");
            success.showAndWait();
        });
    }

    /** Displays the latest local deployment log in a dialog. */
    public void showLogsDialog() {
        File logsDir = com.hrms.deploytool.deploy.ConfigManager.getLogsDir();
        File[] logFiles = logsDir.listFiles((dir, name) -> name.startsWith("deploy_") && name.endsWith(".log"));
        if (logFiles == null || logFiles.length == 0) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Deployment Logs");
            alert.setHeaderText(null);
            alert.setContentText("No local deployment logs found yet.");
            alert.showAndWait();
            return;
        }
        
        // Sort to find the latest
        Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified).reversed());
        File latestLog = logFiles[0];
        
        try {
            String content = Files.readString(latestLog.toPath());
            TextArea textArea = new TextArea(content);
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setFont(javafx.scene.text.Font.font("Consolas", 12));
            
            Dialog<Void> logDialog = new Dialog<>();
            logDialog.setTitle("Latest Log — " + latestLog.getName());
            logDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            logDialog.getDialogPane().setContent(textArea);
            logDialog.getDialogPane().setPrefSize(720, 520);
            logDialog.showAndWait();
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Log Reading Failed");
            alert.setContentText("Could not read log file: " + e.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Cleans up all temporary extraction directories.
     * Called from App.stop() when the application is closing.
     */
    public void cleanup() {
        for (File dir : tempDirsToCleanup) {
            ZipExtractor.deleteRecursively(dir);
        }
        tempDirsToCleanup.clear();
    }
}
