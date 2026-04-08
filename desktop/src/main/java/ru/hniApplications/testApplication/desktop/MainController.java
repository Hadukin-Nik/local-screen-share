package ru.hniApplications.testApplication.desktop;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import ru.hniApplications.testApplication.ScreenCaptureEncoder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
        // 1. Показываем индикатор загрузки на главном экране (чтобы не вешать UI)
        ProgressIndicator spinner = new ProgressIndicator();
        Label loadingLabel = new Label("Поиск аудиоустройств...");
        loadingLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: white;");

        VBox loadingBox = new VBox(15, spinner, loadingLabel);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setStyle("-fx-background-color: #1a1a2e;"); // Под цвет вашей темы

        // Временно ставим загрузочный экран
        root.getChildren().setAll(loadingBox);

        // 2. Асинхронно ищем устройства в фоновом потоке
        CompletableFuture.supplyAsync(ScreenCaptureEncoder::listAllAudioDevices)
                .thenAccept(devices -> Platform.runLater(() -> {
                    // 3. Когда список готов, создаем и показываем диалог (в UI-потоке)
                    AudioDeviceChooserDialog dialog = new AudioDeviceChooserDialog(devices);
                    dialog.initOwner(root.getScene().getWindow());

                    // Ждем выбора пользователя
                    Optional<ScreenCaptureEncoder.AudioDevice> result = dialog.showAndWait();

                    if (result.isPresent()) {
                        // КОРИДОР УСПЕХА: устройство выбрано, продолжаем запуск
                        try {
                            broadcastManager = new BroadcastManager(name, port, fps);

                            // Передаем выбранное устройство
                            broadcastManager.setAudioDevice(result.get());

                            // Привязываем слушатель громкости звука к UI-бару
                            broadcastManager.setAudioLevelListener(level -> {
                                Platform.runLater(() -> {
                                    broadcastView.updateAudioLevel(level);
                                });
                            });
                            // Запуск самого стрима и захвата
                            broadcastManager.start();

                            state = AppState.BROADCASTING;

                            // Устанавливаем и показываем интерфейс трансляции
                            broadcastView.setTitle(name);
                            root.getChildren().setAll(broadcastView);

                            // Запускаем превью
                            broadcastView.startPreview();
                            startBroadcastStatsUpdater();

                        } catch (Exception e) {
                            showError("Ошибка запуска трансляции", e);
                            // Возврат на экран создания трансляции (лобби/меню), если произошла ошибка
                            // Замените lobbyView на вашу панель по умолчанию
                            root.getChildren().setAll((Collection<? extends Node>) lobbyView);
                        }
                    } else {
                        // ОТМЕНА: пользователь закрыл диалог
                        System.out.println("Запуск отменен: аудиоустройство не выбрано.");
                        // Возвращаем стартовый экран
                        // Замените lobbyView на панель, откуда была нажата кнопка "Начать трансляцию"
                        root.getChildren().setAll((Collection<? extends Node>) lobbyView);
                    }
                }))
                .exceptionally(ex -> {
                    // ОБРАБОТКА ОШИБОК ПОИСКА УСТРОЙСТВ
                    Platform.runLater(() -> {
                        showError("Ошибка поиска аудиоустройств", (Exception) ex);
                        // Возврат в меню
                        root.getChildren().setAll((Collection<? extends Node>) lobbyView);
                    });
                    return null;
                });
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