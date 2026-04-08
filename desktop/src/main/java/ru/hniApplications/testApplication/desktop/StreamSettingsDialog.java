package ru.hniApplications.testApplication.desktop;

import javax.swing.*;
import java.awt.*;
import java.util.Vector;

public class StreamSettingsDialog extends JDialog {
    private JComboBox<String> screenSelector;
    private JComboBox<String> resolutionSelector;
    private JComboBox<String> fpsSelector;
    private JComboBox<String> bitrateSelector;
    private boolean isConfirmed = false;

    public StreamSettingsDialog(JFrame parent) {
        super(parent, "Настройки трансляции", true);
        setLayout(new GridLayout(5, 2, 10, 10));
        setSize(400, 250);
        setLocationRelativeTo(parent);

        // 1. Определение всех экранов (мониторов)
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] screens = ge.getScreenDevices();
        Vector<String> screenNames = new Vector<>();
        for (int i = 0; i < screens.length; i++) {
            screenNames.add("Монитор " + (i + 1) + " (" + screens[i].getDisplayMode().getWidth() + "x" + screens[i].getDisplayMode().getHeight() + ")");
        }

        screenSelector = new JComboBox<>(screenNames);
        resolutionSelector = new JComboBox<>(new String[]{"1920x1080", "1280x720", "854x480"});
        fpsSelector = new JComboBox<>(new String[]{"30", "60"});
        bitrateSelector = new JComboBox<>(new String[]{"1000k", "2500k", "5000k"});

        add(new JLabel(" Окно/Экран захвата:")); add(screenSelector);
        add(new JLabel(" Разрешение потока:")); add(resolutionSelector);
        add(new JLabel(" Кадровая частота (FPS):")); add(fpsSelector);
        add(new JLabel(" Битрейт видео:")); add(bitrateSelector);

        JButton startBtn = new JButton("Начать трансляцию");
        startBtn.addActionListener(e -> {
            isConfirmed = true;
            dispose();
        });

        JButton cancelBtn = new JButton("Отмена");
        cancelBtn.addActionListener(e -> dispose());

        add(startBtn);
        add(cancelBtn);
    }

    public boolean isConfirmed() { return isConfirmed; }
    public int getSelectedScreenIndex() { return screenSelector.getSelectedIndex(); }
    public String getSelectedResolution() { return (String) resolutionSelector.getSelectedItem(); }
    public int getSelectedFps() { return Integer.parseInt((String) fpsSelector.getSelectedItem()); }
    public String getSelectedBitrate() { return (String) bitrateSelector.getSelectedItem(); }
}