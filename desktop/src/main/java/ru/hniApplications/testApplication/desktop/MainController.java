package ru.hniApplications.testApplication.desktop;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Path;

public class MainController {

    public enum AppState { LOBBY, BROADCASTING, VIEWING }

    private static final Path RECORDINGS_DIR = Path.of(
            System.getProperty("user.home"), "BroadcastRecordings");

    private final StackPane root = new StackPane();
    private final LobbyView lobbyView;
    private final SessionView sessionView;       // для зрителя
    private final BroadcastView broadcastView;    // для транслирующего

    private BroadcastManager broadcastManager;
    private ViewerManager viewerManager;
    private Timeline statsTimeline;
    private AppState state = AppState.LOBBY;

    public MainController() {
        lobbyView = new LobbyView(this);
        sessionView = new SessionView(this);
        broadcastView = new BroadcastView();

        setupBroadcastView();

        root.getChildren().add(lobbyView.getView());
    }

    public Parent getRoot() {
        return root;
    }

    // ════════════════════════════════════════════════════════
    //  Настройка BroadcastView callbacks
    // ════════════════════════════════════════════════════════

    private void setupBroadcastView() {
        broadcastView.setOnStopBroadcast(this::stopBroadcast);

        broadcastView.setOnStartRecording(() -> {
            if (broadcastManager != null) {
                try {
                    broadcastManager.startRecording(RECORDINGS_DIR);
                    System.out.println("[MainController] Запись начата");
                } catch (Exception e) {
                    System.err.println("Не удалось начать запись: " + e.getMessage());
                    broadcastView.setRecordingState(false);
                }
            }
        });

        broadcastView.setOnStopRecording(() -> {
            if (broadcastManager != null) {
                broadcastManager.stopRecording(mp4Path -> {
                    Platform.runLater(() -> {
                        if (mp4Path != null) {
                            System.out.println("[MainController] Запись сохранена: " + mp4Path);
                        } else {
                            System.err.println("[MainController] Ошибка сохранения записи");
                        }
                    });
                });
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  Трансляция
    // ════════════════════════════════════════════════════════

    public void startBroadcast(String name, int port, int fps) {
        try {
            broadcastManager = new BroadcastManager(name, port, fps);
            broadcastManager.start();

            state = AppState.BROADCASTING;

            // Показываем BroadcastView
            broadcastView.setTitle(name);
            root.getChildren().setAll(broadcastView);

            // Запускаем превью экрана
            broadcastView.startPreview();

            // Статистика
            startBroadcastStatsUpdater();

        } catch (Exception e) {
            showError("Ошибка запуска трансляции", e);
        }
    }

    public void stopBroadcast() {
        broadcastView.stopPreview();
        broadcastView.setRecordingState(false);
        stopStatsUpdater();

        if (broadcastManager != null) {
            broadcastManager.stop();
            broadcastManager = null;
        }

        state = AppState.LOBBY;
        showLobby();
    }

    // ════════════════════════════════════════════════════════
    //  Просмотр
    // ════════════════════════════════════════════════════════

    public void connectToStream(
            ru.hniApplications.testApplication.discovery.DiscoveredService service) {
        connectToManual(service.getHost(), service.getPort());
    }

    public void connectToManual(String host, int port) {
        try {
            viewerManager = new ViewerManager(
                    host, port,
                    image -> Platform.runLater(() -> sessionView.updateFrame(image))
            );
            viewerManager.start();

            state = AppState.VIEWING;
            sessionView.configure(host + ":" + port, false);
            root.getChildren().setAll(sessionView.getView());

        } catch (Exception e) {
            showError("Ошибка подключения", e);
        }
    }

    public void disconnectFromStream() {
        if (viewerManager != null) {
            viewerManager.stop();
            viewerManager = null;
        }
        state = AppState.LOBBY;
        showLobby();
    }

    // ════════════════════════════════════════════════════════
    //  Навигация
    // ════════════════════════════════════════════════════════

    private void showLobby() {
        root.getChildren().setAll(lobbyView.getView());
    }

    // ════════════════════════════════════════════════════════
    //  Статистика трансляции
    // ════════════════════════════════════════════════════════

    private void startBroadcastStatsUpdater() {
        statsTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> {
                    if (broadcastManager != null && broadcastManager.isRunning()) {
                        broadcastView.updateStats(
                                broadcastManager.getFramesSent(),
                                broadcastManager.getClientCount(),
                                broadcastManager.getCapturedWidth(),
                                broadcastManager.getCapturedHeight(),
                                broadcastManager.getActualPort()
                        );
                    }
                }));
        statsTimeline.setCycleCount(Animation.INDEFINITE);
        statsTimeline.play();
    }

    private void stopStatsUpdater() {
        if (statsTimeline != null) {
            statsTimeline.stop();
            statsTimeline = null;
        }
    }

    // ════════════════════════════════════════════════════════
    //  Утилиты
    // ════════════════════════════════════════════════════════

    public BroadcastManager getBroadcastManager() {
        return broadcastManager;
    }

    public AppState getState() {
        return state;
    }

    public void showError(String header, Exception e) {
        e.printStackTrace();
    }

    public void shutdown() {
        broadcastView.stopPreview();
        stopStatsUpdater();
        if (broadcastManager != null) {
            broadcastManager.stop();
            broadcastManager = null;
        }
        if (viewerManager != null) {
            viewerManager.stop();
            viewerManager = null;
        }
    }
}