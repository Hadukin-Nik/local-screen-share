package ru.hniApplications.testApplication.session;

import ru.hniApplications.testApplication.discovery.DiscoveredService;
import ru.hniApplications.testApplication.discovery.DiscoveryListener;
import ru.hniApplications.testApplication.discovery.ServiceAdvertiser;
import ru.hniApplications.testApplication.discovery.ServiceDiscovery;
import ru.hniApplications.testApplication.net.ConnectionListener;
import ru.hniApplications.testApplication.net.FrameListener;
import ru.hniApplications.testApplication.net.RelayServer;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


public class SessionManager {


    private static final int DISCOVERY_SCAN_SECONDS = 4;

    private final String deviceName;
    private final InetAddress bindAddress;
    private final FrameListener frameListener;
    private final ConnectionListener connectionListener;

    private final AtomicBoolean broadcasting = new AtomicBoolean(false);

    private RelayServer relayServer;
    private ServiceAdvertiser advertiser;


    public SessionManager(String deviceName,
                          InetAddress bindAddress,
                          FrameListener frameListener,
                          ConnectionListener connectionListener) {
        this.deviceName = deviceName;
        this.bindAddress = bindAddress;
        this.frameListener = frameListener;
        this.connectionListener = connectionListener;
    }


    public SessionManager(String deviceName, InetAddress bindAddress) {
        this(deviceName, bindAddress, null, null);
    }

    public SessionManager(String deviceName) throws IOException {
        this(deviceName, InetAddress.getLocalHost(), null, null);
    }


    public SessionResult startBroadcast(int port) {

        if (broadcasting.get()) {
            return SessionResult.error(
                    "This SessionManager is already broadcasting");
        }


        DiscoveredService existing = scanForExistingService();
        if (existing != null) {
            return SessionResult.alreadyBroadcasting(existing);
        }


        try {
            relayServer = new RelayServer(port, frameListener, connectionListener);
            relayServer.start();

            advertiser = new ServiceAdvertiser(deviceName);
            advertiser.startAdvertising(port, bindAddress);

            broadcasting.set(true);
            return SessionResult.ok();

        } catch (Exception e) {

            cleanup();
            return SessionResult.error(
                    "Failed to start broadcast: " + e.getMessage());
        }
    }


    public void stopBroadcast() {
        cleanup();
    }


    public boolean isBroadcasting() {
        return broadcasting.get();
    }

    public String getDeviceName() {
        return deviceName;
    }


    private DiscoveredService scanForExistingService() {
        List<DiscoveredService> found = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        ServiceDiscovery probe = new ServiceDiscovery();
        try {
            probe.startListening(new DiscoveryListener() {
                @Override
                public void onServiceFound(DiscoveredService service) {
                    found.add(service);
                    latch.countDown();
                }

                @Override
                public void onServiceLost(DiscoveredService service) {

                }
            }, bindAddress);


            latch.await(DISCOVERY_SCAN_SECONDS, TimeUnit.SECONDS);

        } catch (IOException | InterruptedException e) {


        } finally {
            probe.stopListening();
        }

        return found.isEmpty() ? null : found.get(0);
    }


    private void cleanup() {
        broadcasting.set(false);

        if (advertiser != null) {
            advertiser.stopAdvertising();
            advertiser = null;
        }
        if (relayServer != null) {
            relayServer.stop();
            relayServer = null;
        }
    }
}