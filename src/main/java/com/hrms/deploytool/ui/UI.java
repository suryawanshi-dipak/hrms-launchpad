package com.hrms.deploytool.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;


/**
 * Shared UI factory methods and components.
 * This class abstracts away boilerplate JavaFX setup and CSS class bindings,
 * promoting consistency and reusability across all application pages.
 */
public class UI {

    // ── Layout Builders ───────────────────────────────────────────────────────

    /**
     * Builds a standardized top navigation bar used across main application pages.
     * @param titleText The main title displayed on the left.
     * @param leftIcon The icon node displayed next to the title.
     * @param additionalIcons Any extra icon buttons (like history, settings, etc.) to add to the right side.
     * @return An HBox representing the top bar.
     */
    public static HBox buildTopbar(String titleText, Node leftIcon, Node... additionalIcons) {
        HBox bar = new HBox(8);
        bar.getStyleClass().add("topbar");
        bar.setAlignment(Pos.CENTER_LEFT);

        HBox left = new HBox(8);
        left.setAlignment(Pos.CENTER_LEFT);
        left.getChildren().addAll(leftIcon, UI.topbarTitle(titleText));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox right = new HBox(14);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.getChildren().addAll(additionalIcons);

        bar.getChildren().addAll(left, spacer, right);
        return bar;
    }

    /**
     * Creates a standardized application window container.
     * @param children The layout nodes to place inside the window.
     * @return A VBox styled as the main application window with borders and rounding.
     */
    public static VBox appWindow(Node... children) {
        VBox win = new VBox(0);
        win.getStyleClass().add("app-window");
        win.getChildren().addAll(children);
        return win;
    }

    /**
     * Creates a standard "PAGE X OF Y" frame label.
     * @param text The text to display.
     * @return A styled label.
     */
    public static Label frameLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("frame-label");
        return l;
    }

    // ── Labels ────────────────────────────────────────────────────────────────
    
    /** Base factory for creating a label with specific CSS classes. */
    public static Label label(String text, String... styleClasses) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styleClasses);
        return l;
    }
    
    /** Creates a bold label. */
    public static Label bold(String text, String... styleClasses) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styleClasses);
        l.setStyle(l.getStyle() + "-fx-font-weight:bold;");
        return l;
    }
    
    public static Label topbarTitle(String text) { return label(text, "topbar-title"); }
    public static Label metaLabel(String text)   { return label(text, "topbar-meta"); }
    public static Label eyebrow(String text)     { return label(text, "eyebrow"); }
    public static Label link(String text)        { return label(text, "deploy-log-link"); }
    public static Label checkPill(String text)   { return label(text, "check-pill"); }
    public static Label envReady(String text)    { return label(text, "env-ready"); }

    // ── Buttons ───────────────────────────────────────────────────────────────
    
    public static Button demoBtn(String text) {
        Button b = new Button(text); b.getStyleClass().add("demo-btn"); return b;
    }
    public static Button primaryBtn(String text) {
        Button b = new Button(text); b.getStyleClass().add("btn-primary"); return b;
    }
    public static Button secondaryBtn(String text) {
        Button b = new Button(text); b.getStyleClass().add("btn-secondary"); return b;
    }
    public static Button greenBtn(String text) {
        Button b = new Button(text); b.getStyleClass().add("btn-green"); return b;
    }
    public static Button outlineBtn(String text) {
        Button b = new Button(text); b.getStyleClass().add("btn-outline"); return b;
    }
    
    /** Creates an icon button with a tooltip hover effect. */
    public static Button iconBtn(String icon, String tip) {
        Button b = new Button(icon); b.getStyleClass().add("icon-btn");
        Tooltip.install(b, new Tooltip(tip)); return b;
    }
    
    /** Creates an arrow button (e.g., for transferring files) with a tooltip. */
    public static Button arrowBtn(String arrow, String tip) {
        Button b = new Button(arrow); b.getStyleClass().add("arrow-btn");
        Tooltip.install(b, new Tooltip(tip)); return b;
    }

    // ── Input fields ──────────────────────────────────────────────────────────
    
    /** Creates a standardized text field for settings and inputs. */
    public static TextField textField(String value) {
        TextField f = new TextField(value); f.getStyleClass().add("text-input");
        f.setMaxWidth(Double.MAX_VALUE); return f;
    }
    
    /** Wraps an input field with a label above it. */
    public static VBox fieldGroup(String labelText, Node field) {
        Label l = new Label(labelText); l.getStyleClass().add("field-label");
        VBox g = new VBox(6); g.getChildren().addAll(l, field); return g;
    }

    // ── Card ──────────────────────────────────────────────────────────────────
    
    /** Creates a standardized card layout block. */
    public static VBox card() {
        VBox c = new VBox(0); c.getStyleClass().add("card");
        c.setMaxWidth(Double.MAX_VALUE); return c;
    }

    // ── Icon stand-ins (text-based) ───────────────────────────────────────────
    
    public static Label cloudIcon()   { return styledIcon("☁", "#e6e6e6", 18); }
    public static Label zipIcon()     { return styledIcon("🗜", "#dcb67a", 16); }
    public static Label buildingIcon(){ return styledIcon("🏢", "#9d7cf0", 13); }
    public static Label checkCircle(int sz, String color) { return styledIcon("✓", color, sz); }
    public static Label shieldIcon()  { return styledIcon("🛡", "#7ec97e", 13); }
    public static Label docIcon()     { return styledIcon("📄", "#9a9a9a", 13); }
    public static Label clockIcon()   { return styledIcon("⏱", "#6b6b74", 14); }
    public static Label packageIcon() { return styledIcon("📦", "#4fc3f7", 14); }
    public static Label serverIcon()  { return styledIcon("🖥", "#7ec97e", 14); }

    private static Label styledIcon(String icon, String color, int size) {
        Label l = new Label(icon);
        l.setStyle(String.format("-fx-text-fill:%s;-fx-font-size:%dpx;", color, size));
        return l;
    }

    // ── Misc ──────────────────────────────────────────────────────────────────
    
    /** Creates an empty region acting as vertical spacing. */
    public static Region vspace(double h) {
        Region r = new Region(); r.setMinHeight(h); r.setMaxHeight(h); return r;
    }
}
