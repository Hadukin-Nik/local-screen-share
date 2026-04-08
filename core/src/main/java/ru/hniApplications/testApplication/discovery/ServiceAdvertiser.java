package ru.hniApplications.testApplication.discovery;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;


public class ServiceAdvertiser {

    public static final String SERVICE_TYPE = "_screenrelay._tcp.local.";
    private static final String DEFAULT_NAME_PREFIX = "ScreenRelay-";

    private final String deviceName;
    private JmDNS jmdns;
    private ServiceInfo serviceInfo;

    
    public ServiceAdvertiser(String deviceName) {
        this.deviceName = deviceName;
    }

    
    public ServiceAdvertiser() {
        this(DEFAULT_NAME_PREFIX + ProcessHandle.current().pid());
    }

    
    public void startAdvertising(int port) throws IOException {
        InetAddress address = InetAddress.getLocalHost();
        jmdns = JmDNS.create(address);

        serviceInfo = ServiceInfo.create(
                SERVICE_TYPE,
                deviceName,
                port,
                "ScreenRelay service"
        );

        jmdns.registerService(serviceInfo);
    }

    
    public void startAdvertising(int port, InetAddress bindAddress) throws IOException {
        jmdns = JmDNS.create(bindAddress);

        serviceInfo = ServiceInfo.create(
                SERVICE_TYPE,
                deviceName,
                port,
                "ScreenRelay service"
        );

        jmdns.registerService(serviceInfo);
    }

    
    public void stopAdvertising() {
        if (jmdns != null) {
            jmdns.unregisterAllServices();
            try {
                jmdns.close();
            } catch (IOException ignored) {
            }
            jmdns = null;
            serviceInfo = null;
        }
    }

    public String getDeviceName() {
        return deviceName;
    }
}