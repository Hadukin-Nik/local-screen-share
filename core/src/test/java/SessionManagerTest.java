import org.junit.jupiter.api.*;
import ru.hniApplications.testApplication.discovery.DiscoveredService;
import ru.hniApplications.testApplication.discovery.DiscoveryListener;
import ru.hniApplications.testApplication.discovery.ServiceDiscovery;
import ru.hniApplications.testApplication.session.SessionManager;
import ru.hniApplications.testApplication.session.SessionResult;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SessionManagerTest {

    private SessionManager manager1;
    private SessionManager manager2;

    private InetAddress addr;

    @BeforeEach
    void setUp() throws Exception {
        addr = InetAddress.getLocalHost();
    }

    @AfterEach
    void tearDown() {
        if (manager1 != null) {
            manager1.stopBroadcast();
            manager1 = null;
        }
        if (manager2 != null) {
            manager2.stopBroadcast();
            manager2 = null;
        }
    }

    

    @Test
    @Order(1)
    void firstManagerStartsSuccessfully() throws Exception {
        manager1 = new SessionManager("Device-One", addr);
        SessionResult result = manager1.startBroadcast(10001);

        assertTrue(result.isSuccess(), result.getMessage());
        assertTrue(manager1.isBroadcasting());
    }

    

    @Test
    @Order(2)
    void secondManagerIsRejected() throws Exception {
        manager1 = new SessionManager("Device-One", addr);
        SessionResult r1 = manager1.startBroadcast(10002);
        assertTrue(r1.isSuccess(), r1.getMessage());

        
        Thread.sleep(2000);

        manager2 = new SessionManager("Device-Two", addr);
        SessionResult r2 = manager2.startBroadcast(10003);

        assertFalse(r2.isSuccess(), "Second manager should be rejected");
        assertFalse(manager2.isBroadcasting());
        assertTrue(r2.getMessage().contains("Device-One"),
                "Error message should contain the device name " +
                        "that is already broadcasting. Received: " + r2.getMessage());
        assertTrue(r2.getExistingService().isPresent());
    }

    

    @Test
    @Order(3)
    void secondManagerStartsAfterFirstStops() throws Exception {
        manager1 = new SessionManager("Device-One", addr);
        SessionResult r1 = manager1.startBroadcast(10004);
        assertTrue(r1.isSuccess(), r1.getMessage());

        
        Thread.sleep(2000);

        
        manager1.stopBroadcast();
        assertFalse(manager1.isBroadcasting());

        
        
        Thread.sleep(3000);

        manager2 = new SessionManager("Device-Two", addr);
        SessionResult r2 = manager2.startBroadcast(10005);

        assertTrue(r2.isSuccess(), "Second broadcast should start. " +
                "Error: " + r2.getMessage());
        assertTrue(manager2.isBroadcasting());
    }

    

    @Test
    @Order(4)
    void doubleStartOnSameManager() throws Exception {
        manager1 = new SessionManager("Device-One", addr);
        SessionResult r1 = manager1.startBroadcast(10006);
        assertTrue(r1.isSuccess());

        SessionResult r2 = manager1.startBroadcast(10007);
        assertFalse(r2.isSuccess(), "Restart should return an error");
        assertTrue(r2.getMessage().contains("already broadcasting"));
    }

    

    @Test
    @Order(5)
    void multipleStopsAreSafe() throws Exception {
        manager1 = new SessionManager("Device-One", addr);
        manager1.startBroadcast(10008);

        assertDoesNotThrow(() -> {
            manager1.stopBroadcast();
            manager1.stopBroadcast();
            manager1.stopBroadcast();
        });

        assertFalse(manager1.isBroadcasting());
    }

    @Test
    void crashRecovery() throws Exception {
        
        manager1 = new SessionManager("Device-Crash", addr);
        SessionResult r1 = manager1.startBroadcast(10009);
        assertTrue(r1.isSuccess(), r1.getMessage());

        
        Thread.sleep(2000);

        manager1.stopBroadcast();
        manager1 = null;

        
        
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    SessionManager fresh = new SessionManager("Device-New", addr);
                    try {
                        SessionResult r = fresh.startBroadcast(10010);
                        if (r.isSuccess()) {
                            assertTrue(fresh.isBroadcasting());
                            
                            manager2 = fresh;
                        } else {
                            fresh.stopBroadcast();
                            fail("Broadcast not yet available: " + r.getMessage());
                        }
                    } catch (Exception e) {
                        fresh.stopBroadcast();
                        fail(e);
                    }
                });
    }

    

    @Test
    @Order(7)
    void serviceIsDiscoverableAfterStart() throws Exception {
        manager1 = new SessionManager("Device-Visible", addr);
        SessionResult r = manager1.startBroadcast(10011);
        assertTrue(r.isSuccess(), r.getMessage());

        
        List<DiscoveredService> found = new CopyOnWriteArrayList<>();
        ServiceDiscovery externalDiscovery = new ServiceDiscovery();
        try {
            externalDiscovery.startListening(new DiscoveryListener() {
                @Override
                public void onServiceFound(DiscoveredService service) {
                    found.add(service);
                }

                @Override
                public void onServiceLost(DiscoveredService service) {
                }
            }, addr);

            await().atMost(10, TimeUnit.SECONDS)
                    .pollInterval(200, TimeUnit.MILLISECONDS)
                    .until(() -> found.stream()
                            .anyMatch(s -> s.getDeviceName().startsWith("Device-Visible")));

            DiscoveredService svc = found.stream()
                    .filter(s -> s.getDeviceName().startsWith("Device-Visible"))
                    .findFirst()
                    .orElseThrow();

            assertEquals(10011, svc.getPort());
        } finally {
            externalDiscovery.stopListening();
        }
    }
}