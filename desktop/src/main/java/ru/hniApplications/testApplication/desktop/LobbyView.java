package ru.hniApplications.testApplication.desktop;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import ru.hniApplications.testApplication.discovery.DiscoveredService;

import java.util.List;

public class LobbyView {

    private final MainController controller;
    private final VBox root;

    private final TextField nameField;
    private final Spinner<Integer> portSpinner;
    private final Spinner<Integer> fpsSpinner;

    private final TextField hostField;
    private final Spinner<Integer> connectPortSpinner;
    private final ListView<DiscoveredService> serviceList;

    public LobbyView(MainController controller) {
        this.controller = controller;

        
        Label title = new Label("LocalScreenShare");
        title.setFont(Font.font("System", FontWeight.BOLD, 28));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("Share your screen over local network");
        subtitle.setFont(Font.font("System", 14));
        subtitle.setTextFill(Color.web("#aaaacc"));

        VBox header = new VBox(4, title, subtitle);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(20, 0, 30, 0));

        
        Label bcTitle = new Label("\uD83D\uDCE1  Start Broadcast");
        bcTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        bcTitle.setTextFill(Color.WHITE);

        nameField = new TextField(System.getProperty("user.name", "MyScreen"));
        nameField.setPromptText("Enter name...");
        styleTextField(nameField);

        portSpinner = new Spinner<>(0, 65535, 0);
        portSpinner.setEditable(true);
        portSpinner.setPrefWidth(150);
        styleSpinner(portSpinner);

        fpsSpinner = new Spinner<>(1, 60, 30);
        fpsSpinner.setEditable(true);
        fpsSpinner.setPrefWidth(150);
        styleSpinner(fpsSpinner);

        Button startBtn = new Button("\u25B6  Start Broadcast");
        startBtn.setMaxWidth(Double.MAX_VALUE);
        startBtn.setStyle(
                "-fx-background-color: #e94560; -fx-text-fill: white; "
                        + "-fx-font-size: 14px; -fx-font-weight: bold; "
                        + "-fx-padding: 12 24; -fx-background-radius: 8;");
        startBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) name = "Screen";
            controller.startBroadcast(name, portSpinner.getValue(), fpsSpinner.getValue());
        });

        VBox broadcastCard = new VBox(12,
                bcTitle,
                styledLabel("Broadcast name:"), nameField,
                styledLabel("Port (0 = auto):"), portSpinner,
                styledLabel("FPS:"), fpsSpinner,
                new Separator(),
                startBtn
        );
        styleCard(broadcastCard);

        
        Label viewTitle = new Label("\uD83D\uDCFA  Join");
        viewTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        viewTitle.setTextFill(Color.WHITE);

        serviceList = new ListView<>();
        serviceList.setPrefHeight(150);
        serviceList.setPlaceholder(new Label("Searching for broadcasts..."));
        serviceList.setStyle(
                "-fx-background-color: #16213e; -fx-background-radius: 6; "
                        + "-fx-border-color: #0f3460; -fx-border-radius: 6;");
        serviceList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DiscoveredService item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setText(item.getDeviceName() + "  —  "
                            + item.getHost() + ":" + item.getPort());
                    setTextFill(Color.web("#e0e0ff"));
                    setStyle("-fx-background-color: transparent;");
                }
            }
        });

        Button joinBtn = new Button("Connect");
        joinBtn.setMaxWidth(Double.MAX_VALUE);
        joinBtn.setStyle(
                "-fx-background-color: #0f3460; -fx-text-fill: white; "
                        + "-fx-font-size: 13px; -fx-padding: 10 20; "
                        + "-fx-background-radius: 8;");
        joinBtn.setOnAction(e -> {
            DiscoveredService selected = serviceList.getSelectionModel().getSelectedItem();
            if (selected != null) controller.connectToStream(selected);
        });

        hostField = new TextField("localhost");
        hostField.setPromptText("Host");
        styleTextField(hostField);

        connectPortSpinner = new Spinner<>(1, 65535, 12345);
        connectPortSpinner.setEditable(true);
        connectPortSpinner.setPrefWidth(150);
        styleSpinner(connectPortSpinner);

        Button connectBtn = new Button("Manual connect");
        connectBtn.setMaxWidth(Double.MAX_VALUE);
        connectBtn.setStyle(
                "-fx-background-color: #533483; -fx-text-fill: white; "
                        + "-fx-font-size: 13px; -fx-padding: 10 20; "
                        + "-fx-background-radius: 8;");
        connectBtn.setOnAction(e -> {
            String host = hostField.getText().trim();
            if (!host.isEmpty()) {
                controller.connectToManual(host, connectPortSpinner.getValue());
            }
        });

        VBox viewerCard = new VBox(10,
                viewTitle,
                styledLabel("Discovered broadcasts:"), serviceList, joinBtn,
                new Separator(),
                styledLabel("Or enter address manually:"), hostField, connectPortSpinner, connectBtn
        );
        styleCard(viewerCard);

        
        HBox cards = new HBox(20, broadcastCard, viewerCard);
        cards.setAlignment(Pos.CENTER);
        HBox.setHgrow(broadcastCard, Priority.ALWAYS);
        HBox.setHgrow(viewerCard, Priority.ALWAYS);

        root = new VBox(0, header, cards);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(cards, Priority.ALWAYS);
    }

    void refreshServices(List<DiscoveredService> services) {
        serviceList.getItems().setAll(services);
    }

    Node getView() {
        return root;
    }

    

    private Label styledLabel(String text) {
        Label l = new Label(text);
        l.setTextFill(Color.web("#ccccee"));
        l.setFont(Font.font("System", 12));
        return l;
    }

    private void styleTextField(TextField tf) {
        tf.setStyle(
                "-fx-background-color: #16213e; -fx-text-fill: white; "
                        + "-fx-prompt-text-fill: #667; -fx-border-color: #0f3460; "
                        + "-fx-border-radius: 6; -fx-background-radius: 6; "
                        + "-fx-padding: 8;");
    }

    private void styleSpinner(Spinner<?> sp) {
        sp.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #0f3460; "
                        + "-fx-border-radius: 6; -fx-background-radius: 6;");
    }

    private void styleCard(VBox card) {
        card.setPadding(new Insets(20));
        card.setStyle(
                "-fx-background-color: #16213e; -fx-background-radius: 12; "
                        + "-fx-border-color: #0f3460; -fx-border-radius: 12; "
                        + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 4);");
    }
}