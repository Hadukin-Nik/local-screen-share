package ru.hniApplications.testApplication.discovery;

import java.util.Objects;


public class DiscoveredService {

    private final String host;
    private final int port;
    private final String deviceName;

    public DiscoveredService(String host, int port, String deviceName) {
        this.host = host;
        this.port = port;
        this.deviceName = deviceName;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDeviceName() {
        return deviceName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiscoveredService that = (DiscoveredService) o;
        return port == that.port
                && Objects.equals(host, that.host)
                && Objects.equals(deviceName, that.deviceName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, deviceName);
    }

    @Override
    public String toString() {
        return "DiscoveredService{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", deviceName='" + deviceName + '\'' +
                '}';
    }
}