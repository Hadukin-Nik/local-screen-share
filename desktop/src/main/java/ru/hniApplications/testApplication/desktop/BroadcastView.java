package ru.hniApplications.testApplication.desktop;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
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
    private final ProgressBar audioLevelBar;

    private BroadcastPreviewCapture previewCapture;
    private boolean isRecording = false;

    private Runnable onStopBroadcast;
    private Runnable onStartRecording;
    private Runnable onStopRecording;

    public BroadcastView() {
        titleLabel = new Label("Трансляция");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        previewImageView = new ImageView();
        previewImageView.setPreserveRatio(true);
        previewImageView.setSmooth(true);

        StackPane previewContainer = new StackPane(previewImageView);
        previewContainer.setStyle(
                "-fx-background-color: #1a1a2e; "
                        + "-fx-border-color: #333; "
                        + "-fx-border-radius: 4; "
                        + "-fx-min-height: 300;");
        previewImageView.fitWidthProperty().bind(previewContainer.widthProperty().subtract(20));
        previewImageView.fitHeightProperty().bind(previewContainer.heightProperty().subtract(20));

        recordIndicator = new Circle(6, Color.RED);
        recordIndicator.setVisible(false);

        recordingStatusLabel = new Label("");
        recordingStatusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");

        HBox recordingBar = new HBox(6, recordIndicator, recordingStatusLabel);
        recordingBar.setAlignment(Pos.CENTER_LEFT);

        audioLevelBar = new ProgressBar(0);
        audioLevelBar.setPrefWidth(100);
        audioLevelBar.setStyle("-fx-accent: #2ecc71;");
        HBox audioBox = new HBox(8, new Label("\uD83D\uDD0A"), audioLevelBar);
        audioBox.setAlignment(Pos.CENTER_RIGHT);

        HBox topBar = new HBox(12, titleLabel, recordingBar);
        topBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(recordingBar, Priority.ALWAYS);

        statsLabel = new Label("Ожидание...");
        statsLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #aaa;");

        recordButton = new Button("⏺ Начать запись");
        recordButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-size: 13px; -fx-padding: 8 20; -fx-background-radius: 6;");
        recordButton.setOnAction(e -> toggleRecording());

        stopButton = new Button("Остановить трансляцию");
        stopButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 10 24; -fx-background-radius: 6;");
        stopButton.setOnAction(e -> {
            if (onStopBroadcast != null) onStopBroadcast.run();
        });

        HBox buttonBar = new HBox(12, recordButton, stopButton);
        buttonBar.setAlignment(Pos.CENTER);

        HBox statsAndAudioBox = new HBox(statsLabel);
        HBox.setHgrow(statsLabel, Priority.ALWAYS);
        statsAndAudioBox.getChildren().add(audioBox);
        statsAndAudioBox.setAlignment(Pos.CENTER_LEFT);

        VBox bottomBox = new VBox(10, statsAndAudioBox, buttonBar);
        bottomBox.setAlignment(Pos.CENTER);
        bottomBox.setPadding(new Insets(8, 0, 0, 0));

        VBox centerBox = new VBox(12, topBar, previewContainer, bottomBox);
        centerBox.setPadding(new Insets(16));
        centerBox.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(previewContainer, Priority.ALWAYS);

        setCenter(centerBox);
    }

    public void updateAudioLevel(double level) {
        Platform.runLater(() -> {
            audioLevelBar.setProgress(level * 3); // Мультипликатор для более выразительной визуализации
            if (level > 0.8) {
                audioLevelBar.setStyle("-fx-accent: #e74c3c;"); // Красный при перегрузе
            } else if (level > 0.5) {
                audioLevelBar.setStyle("-fx-accent: #f1c40f;"); // Желтый на средних
            } else {
                audioLevelBar.setStyle("-fx-accent: #2ecc71;"); // Зеленый в норме
            }
        });
    }

    private void toggleRecording() {
        if (!isRecording) {
            if (onStartRecording != null) onStartRecording.run();
            setRecordingState(true);
        } else {
            if (onStopRecording != null) onStopRecording.run();
            setRecordingState(false);
        }
    }

    public void setRecordingState(boolean recording) {
        this.isRecording = recording;
        Platform.runLater(() -> {
            if (recording) {
                recordButton.setText("⏹ Остановить запись");
                recordButton.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white; -fx-font-size: 13px; -fx-padding: 8 20; -fx-background-radius: 6;");
                recordIndicator.setVisible(true);
                recordingStatusLabel.setText("REC");
            } else {
                recordButton.setText("⏺ Начать запись");
                recordButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-size: 13px; -fx-padding: 8 20; -fx-background-radius: 6;");
                recordIndicator.setVisible(false);
                recordingStatusLabel.setText("");
            }
        });
    }

    public void setOnStopBroadcast(Runnable handler) { this.onStopBroadcast = handler; }
    public void setOnStartRecording(Runnable handler) { this.onStartRecording = handler; }
    public void setOnStopRecording(Runnable handler) { this.onStopRecording = handler; }

    public void startPreview() {
        stopPreview();
        previewCapture = new BroadcastPreviewCapture(image -> Platform.runLater(() -> previewImageView.setImage(image)));
        previewCapture.start();
    }
    public void stopPreview() {
        if (previewCapture != null) {
            previewCapture.stop();
            previewCapture = null;
        }
        previewImageView.setImage(null);
        updateAudioLevel(0); // Сброс индикатора звука
    }

    public void updateStats(long framesSent, int clientCount, int width, int height, int port) {
        String recMark = isRecording ? " | 🔴 REC" : "";
        statsLabel.setText(String.format("Port: %d | %d×%d | Frames: %d | Viewers: %d%s", port, width, height, framesSent, clientCount, recMark));
    }
    public void setTitle(String name) { titleLabel.setText("Трансляция: " + name); }
}