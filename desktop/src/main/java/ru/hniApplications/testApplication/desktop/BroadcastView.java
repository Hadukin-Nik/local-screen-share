package ru.hniApplications.testApplication.desktop;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

public class BroadcastView extends BorderPane {

    private final Label titleLabel;
    private final Label statsLabel;
    private final Label recordingStatusLabel;
    private final ImageView previewImageView;
    private final Button stopButton;
    private final Button recordButton;
    private final Circle recordIndicator;

    private BroadcastPreviewCapture previewCapture;
    private boolean isRecording = false;

    // Callbacks
    private Runnable onStopBroadcast;
    private Runnable onStartRecording;
    private Runnable onStopRecording;

    public BroadcastView() {
        // ── Заголовок ──
        titleLabel = new Label("Translation");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // ── Превью ──
        previewImageView = new ImageView();
        previewImageView.setPreserveRatio(true);
        previewImageView.setSmooth(true);

        StackPane previewContainer = new StackPane(previewImageView);
        previewContainer.setStyle(
                "-fx-background-color: #1a1a2e; "
                        + "-fx-border-color: #333; "
                        + "-fx-border-radius: 4; "
                        + "-fx-min-height: 300;");
        previewImageView.fitWidthProperty().bind(
                previewContainer.widthProperty().subtract(20));
        previewImageView.fitHeightProperty().bind(
                previewContainer.heightProperty().subtract(20));

        // ── Индикатор записи ──
        recordIndicator = new Circle(6, Color.RED);
        recordIndicator.setVisible(false);

        recordingStatusLabel = new Label("");
        recordingStatusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");

        HBox recordingBar = new HBox(6, recordIndicator, recordingStatusLabel);
        recordingBar.setAlignment(Pos.CENTER_LEFT);

        // ── Верхняя панель ──
        HBox topBar = new HBox(12, titleLabel, recordingBar);
        topBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(recordingBar, Priority.ALWAYS);

        // ── Статистика ──
        statsLabel = new Label("Awaiting...");
        statsLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #aaa;");

        // ── Кнопки ──
        recordButton = new Button("⏺ Start Recording");
        recordButton.setStyle(
                "-fx-background-color: #c0392b; "
                        + "-fx-text-fill: white; "
                        + "-fx-font-size: 13px; "
                        + "-fx-padding: 8 20; "
                        + "-fx-background-radius: 6;");
        recordButton.setOnAction(e -> toggleRecording());

        stopButton = new Button("Stop translation");
        stopButton.setStyle(
                "-fx-background-color: #e74c3c; "
                        + "-fx-text-fill: white; "
                        + "-fx-font-size: 14px; "
                        + "-fx-padding: 10 24; "
                        + "-fx-background-radius: 6;");
        stopButton.setOnAction(e -> {
            if (onStopBroadcast != null) onStopBroadcast.run();
        });

        // ── Нижняя панель ──
        HBox buttonBar = new HBox(12, recordButton, stopButton);
        buttonBar.setAlignment(Pos.CENTER);

        VBox bottomBox = new VBox(10, statsLabel, buttonBar);
        bottomBox.setAlignment(Pos.CENTER);
        bottomBox.setPadding(new Insets(8, 0, 0, 0));

        // ── Основной layout ──
        VBox centerBox = new VBox(12, topBar, previewContainer, bottomBox);
        centerBox.setPadding(new Insets(16));
        centerBox.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(previewContainer, Priority.ALWAYS);

        setCenter(centerBox);
    }

    private void toggleRecording() {
        if (!isRecording) {
            // Начинаем запись
            if (onStartRecording != null) onStartRecording.run();
            setRecordingState(true);
        } else {
            // Останавливаем запись
            if (onStopRecording != null) onStopRecording.run();
            setRecordingState(false);
        }
    }

    /**
     * Обновляет визуальное состояние кнопки записи и индикатора.
     */
    public void setRecordingState(boolean recording) {
        this.isRecording = recording;
        Platform.runLater(() -> {
            if (recording) {
                recordButton.setText("⏹ Stop recording");
                recordButton.setStyle(
                        "-fx-background-color: #2c3e50; "
                                + "-fx-text-fill: white; "
                                + "-fx-font-size: 13px; "
                                + "-fx-padding: 8 20; "
                                + "-fx-background-radius: 6;");
                recordIndicator.setVisible(true);
                recordingStatusLabel.setText("REC");
            } else {
                recordButton.setText("⏺ Start recording");
                recordButton.setStyle(
                        "-fx-background-color: #c0392b; "
                                + "-fx-text-fill: white; "
                                + "-fx-font-size: 13px; "
                                + "-fx-padding: 8 20; "
                                + "-fx-background-radius: 6;");
                recordIndicator.setVisible(false);
                recordingStatusLabel.setText("");
            }
        });
    }

    // ── Callbacks ──

    public void setOnStopBroadcast(Runnable handler) {
        this.onStopBroadcast = handler;
    }

    public void setOnStartRecording(Runnable handler) {
        this.onStartRecording = handler;
    }

    public void setOnStopRecording(Runnable handler) {
        this.onStopRecording = handler;
    }

    // ── Превью ──

    public void startPreview() {
        stopPreview();
        previewCapture = new BroadcastPreviewCapture(
                image -> Platform.runLater(() -> previewImageView.setImage(image))
        );
        previewCapture.start();
    }

    public void stopPreview() {
        if (previewCapture != null) {
            previewCapture.stop();
            previewCapture = null;
        }
        previewImageView.setImage(null);
    }

    // ── Статистика ──

    public void updateStats(long framesSent, int clientCount,
                            int width, int height, int port) {
        String recMark = isRecording ? " | 🔴 REC" : "";
        statsLabel.setText(String.format(
                "Port: %d | %d×%d | Frames: %d | Viewers: %d%s",
                port, width, height, framesSent, clientCount, recMark));
    }

    public void setTitle(String name) {
        titleLabel.setText("Translation: " + name);
    }
}