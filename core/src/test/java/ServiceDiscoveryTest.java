
import org.junit.jupiter.api.*;
import ru.hniApplications.testApplication.discovery.DiscoveredService;
import ru.hniApplications.testApplication.discovery.DiscoveryListener;
import ru.hniApplications.testApplication.discovery.ServiceAdvertiser;
import ru.hniApplications.testApplication.discovery.ServiceDiscovery;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;


class ServiceDiscoveryTest {

    private ServiceAdvertiser advertiser1;
    private ServiceAdvertiser advertiser2;
    private ServiceDiscovery discovery;

    @AfterEach
    void tearDown() {
        if (discovery != null) discovery.stopListening();
        if (advertiser1 != null) advertiser1.stopAdvertising();
        if (advertiser2 != null) advertiser2.stopAdvertising();
    }

    

    @Test
    void discoveryFindsAdvertisedService() throws Exception {
        InetAddress loopback = InetAddress.getLocalHost();
        int port = 9001;

        List<DiscoveredService> found = new CopyOnWriteArrayList<>();
        List<DiscoveredService> lost = new CopyOnWriteArrayList<>();

        discovery = new ServiceDiscovery();
        discovery.startListening(new DiscoveryListener() {
            @Override
            public void onServiceFound(DiscoveredService service) {
                found.add(service);
            }

            @Override
            public void onServiceLost(DiscoveredService service) {
                lost.add(service);
            }
        }, loopback);

        advertiser1 = new ServiceAdvertiser("TestDevice-A");
        advertiser1.startAdvertising(port, loopback);

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> found.stream()
                        .anyMatch(s -> s.getDeviceName().startsWith("TestDevice-A")));

        DiscoveredService service = found.stream()
                .filter(s -> s.getDeviceName().startsWith("TestDevice-A"))
                .findFirst()
                .orElseThrow();

        assertEquals(port, service.getPort());
        assertNotNull(service.getHost());
        assertFalse(service.getHost().isEmpty());
    }

    

    @Test
    void discoveryDetectsServiceRemoval() throws Exception {
        InetAddress addr = InetAddress.getLocalHost();
        int port = 9002;

        List<DiscoveredService> found = new CopyOnWriteArrayList<>();

        discovery = new ServiceDiscovery();
        discovery.startListening(new DiscoveryListener() {
            @Override
            public void onServiceFound(DiscoveredService service) {
                found.add(service);
            }

            @Override
            public void onServiceLost(DiscoveredService service) {
                
            }
        }, addr);

        advertiser1 = new ServiceAdvertiser("TestDevice-B");
        advertiser1.startAdvertising(port, addr);

        
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> found.stream()
                        .anyMatch(s -> s.getDeviceName().startsWith("TestDevice-B")));

        
        advertiser1.stopAdvertising();
        advertiser1 = null;

        
        
        discovery.stopListening();

        List<DiscoveredService> freshFound = new CopyOnWriteArrayList<>();

        discovery = new ServiceDiscovery();
        discovery.startListening(new DiscoveryListener() {
            @Override
            public void onServiceFound(DiscoveredService service) {
                freshFound.add(service);
            }

            @Override
            public void onServiceLost(DiscoveredService service) {
            }
        }, addr);

        
        Thread.sleep(6000);

        boolean stillVisible = freshFound.stream()
                .anyMatch(s -> s.getDeviceName().startsWith("TestDevice-B"));
        assertFalse(stillVisible,
                "After stopping Advertiser, the service should not be discovered again");
    }

    

    @Test
    void discoveryFindsTwoServices() throws Exception {
        InetAddress loopback = InetAddress.getLocalHost();
        int portA = 9003;
        int portB = 9004;

        List<DiscoveredService> found = new CopyOnWriteArrayList<>();

        discovery = new ServiceDiscovery();
        discovery.startListening(new DiscoveryListener() {
            @Override
            public void onServiceFound(DiscoveredService service) {
                found.add(service);
            }

            @Override
            public void onServiceLost(DiscoveredService service) {
                
            }
        }, loopback);

        advertiser1 = new ServiceAdvertiser("DeviceAlpha");
        advertiser1.startAdvertising(portA, loopback);

        advertiser2 = new ServiceAdvertiser("DeviceBeta");
        advertiser2.startAdvertising(portB, loopback);

        
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> {
                    boolean hasAlpha = found.stream()
                            .anyMatch(s -> s.getDeviceName().startsWith("DeviceAlpha"));
                    boolean hasBeta = found.stream()
                            .anyMatch(s -> s.getDeviceName().startsWith("DeviceBeta"));
                    return hasAlpha && hasBeta;
                });

        DiscoveredService alpha = found.stream()
                .filter(s -> s.getDeviceName().startsWith("DeviceAlpha"))
                .findFirst()
                .orElseThrow();

        DiscoveredService beta = found.stream()
                .filter(s -> s.getDeviceName().startsWith("DeviceBeta"))
                .findFirst()
                .orElseThrow();

        assertEquals(portA, alpha.getPort());
        assertEquals(portB, beta.getPort());
        assertNotEquals(alpha.getDeviceName(), beta.getDeviceName());
    }

    

    @Test
    void reAdvertiseAfterStop() throws Exception {
        InetAddress loopback = InetAddress.getLocalHost();
        int port = 9005;

        List<DiscoveredService> found = new CopyOnWriteArrayList<>();

        discovery = new ServiceDiscovery();
        discovery.startListening(new DiscoveryListener() {
            @Override
            public void onServiceFound(DiscoveredService service) {
                found.add(service);
            }

            @Override
            public void onServiceLost(DiscoveredService service) {
            }
        }, loopback);

        
        advertiser1 = new ServiceAdvertiser("DeviceRestart");
        advertiser1.startAdvertising(port, loopback);

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> found.stream()
                        .anyMatch(s -> s.getDeviceName().startsWith("DeviceRestart")));

        long countAfterFirst = found.stream()
                .filter(s -> s.getDeviceName().startsWith("DeviceRestart"))
                .count();

        
        advertiser1.stopAdvertising();

        
        Thread.sleep(2000);

        
        advertiser1 = new ServiceAdvertiser("DeviceRestart");
        advertiser1.startAdvertising(port, loopback);

        
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> found.stream()
                        .filter(s -> s.getDeviceName().startsWith("DeviceRestart"))
                        .count() > countAfterFirst);
    }

    

    @Test
    void stopListeningPreventsCallbacks() throws Exception {
        InetAddress loopback = InetAddress.getLocalHost();

        List<DiscoveredService> found = new CopyOnWriteArrayList<>();

        discovery = new ServiceDiscovery();
        discovery.startListening(new DiscoveryListener() {
            @Override
            public void onServiceFound(DiscoveredService service) {
                found.add(service);
            }

            @Override
            public void onServiceLost(DiscoveredService service) {
            }
        }, loopback);

        
        discovery.stopListening();
        discovery = null;

        
        advertiser1 = new ServiceAdvertiser("DeviceGhost");
        advertiser1.startAdvertising(9006, loopback);

        
        Thread.sleep(5000);

        boolean ghostFound = found.stream()
                .anyMatch(s -> s.getDeviceName().startsWith("DeviceGhost"));
        assertFalse(ghostFound, "After stopListening, no notifications should arrive");
    }
}
