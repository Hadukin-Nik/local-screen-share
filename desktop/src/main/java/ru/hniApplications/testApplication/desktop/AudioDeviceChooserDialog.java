package ru.hniApplications.testApplication.desktop;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import ru.hniApplications.testApplication.ScreenCaptureEncoder.AudioDevice;

import java.util.List;

public class AudioDeviceChooserDialog extends Dialog<AudioDevice> {

    public AudioDeviceChooserDialog(List<AudioDevice> devices) {
        setTitle("Выбор источника звука");
        setHeaderText("Выберите аудиоустройство для трансляции\n(включая системный звук):");

        ButtonType connectButtonType = new ButtonType("Применить", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(connectButtonType, ButtonType.CANCEL);

        ListView<AudioDevice> listView = new ListView<>();
        listView.setItems(FXCollections.observableArrayList(devices));
        listView.setPrefHeight(200);
        listView.setPrefWidth(350);
        if (!devices.isEmpty()) {
            listView.getSelectionModel().selectFirst();
        }

        VBox content = new VBox(10, new Label("Доступные устройства:"), listView);
        content.setPadding(new Insets(10));
        getDialogPane().setContent(content);

        setResultConverter(dialogButton -> {
            if (dialogButton == connectButtonType) {
                return listView.getSelectionModel().getSelectedItem();
            }
            return null;
        });
    }
}