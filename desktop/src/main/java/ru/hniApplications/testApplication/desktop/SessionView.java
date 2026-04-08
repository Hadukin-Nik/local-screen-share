package ru.hniApplications.testApplication.desktop;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class SessionView {

    private final MainController controller;
    private final BorderPane root;

    private final Label titleLabel;
    private final Label statusLabel;
    private final Label statsLabel;
    private final ImageView imageView;
    private final Button actionButton;

    private boolean isBroadcasting;

    public SessionView(MainController controller) {
        this.controller = controller;

        
        titleLabel = new Label("Session");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.WHITE);

        statusLabel = new Label("");
        statusLabel.setFont(Font.font("System", 13));
        statusLabel.setTextFill(Color.web("#88cc88"));

        statsLabel = new Label("");
        statsLabel.setFont(Font.font("System", 12));
        statsLabel.setTextFill(Color.web("#aaaacc"));

        actionButton = new Button("End");
        actionButton.setStyle(
                "-fx-background-color: #e94560; -fx-text-fill: white; "
                        + "-fx-font-size: 13px; -fx-font-weight: bold; "
                        + "-fx-padding: 8 24; -fx-background-radius: 8;");
        actionButton.setOnAction(e -> onAction());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox leftInfo = new VBox(4, titleLabel, statusLabel, statsLabel);
        leftInfo.setAlignment(Pos.CENTER_LEFT);

        HBox topBar = new HBox(16, leftInfo, spacer, actionButton);
        topBar.setAlignment(Pos.CENTER);
        topBar.setPadding(new Insets(16, 20, 16, 20));
        topBar.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #0f3460; "
                        + "-fx-border-width: 0 0 1 0;");

        
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        StackPane centerPane = new StackPane(imageView);
        centerPane.setStyle("-fx-background-color: #0a0a1a;");
        centerPane.setPadding(new Insets(8));

        imageView.fitWidthProperty().bind(centerPane.widthProperty().subtract(16));
        imageView.fitHeightProperty().bind(centerPane.heightProperty().subtract(16));

        root = new BorderPane();
        root.setStyle("-fx-background-color: #1a1a2e;");
        root.setTop(topBar);
        root.setCenter(centerPane);
    }

    void configure(String title, boolean isBroadcasting) {
        this.isBroadcasting = isBroadcasting;
        titleLabel.setText(title);
        actionButton.setText(isBroadcasting ? "\u23F9  Stop" : "\u2715  Disconnect");
        statsLabel.setText("");
        imageView.setImage(null);

        if (isBroadcasting) {
            startStatsUpdater();
        }
    }

    void setStatusText(String text) {
        statusLabel.setText(text);
    }

    void updateFrame(WritableImage image) {
        imageView.setImage(image);
    }

    Node getView() {
        return root;
    }

    private void onAction() {
        if (isBroadcasting) {
            controller.stopBroadcast();
        } else {
            controller.disconnectFromStream();
        }
    }

    private void startStatsUpdater() {
        Thread statsThread = new Thread(() -> {
            while (controller.getState() == MainController.AppState.BROADCASTING) {
                BroadcastManager bm = controller.getBroadcastManager();
                if (bm != null) {
                    String stats = String.format(
                            "Frames sent: %d  |  Connected clients: %d",
                            bm.getFramesSent(),
                            bm.getClientCount()
                    );
                    Platform.runLater(() -> statsLabel.setText(stats));
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "stats-updater");
        statsThread.setDaemon(true);
        statsThread.start();
    }
}