
import org.junit.jupiter.api.*;
import ru.hniApplications.testApplication.FramePacket;
import ru.hniApplications.testApplication.FrameType;
import ru.hniApplications.testApplication.net.ConnectionListener;
import ru.hniApplications.testApplication.net.RelayClient;
import ru.hniApplications.testApplication.net.RelayServer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;


class RelayTransportTest {

    private RelayServer server;
    private RelayClient client;

    @AfterEach
    void tearDown() {
        
        
        if (client != null) {
            client.disconnect();
        }
        if (server != null) {
            server.stop();
        }
    }

    

    @Test
    void singlePacketFromClientToServer() throws Exception {
        
        AtomicReference<FramePacket> received = new AtomicReference<>();

        
        server = new RelayServer(0, received::set, null);
        server.start();

        
        client = new RelayClient("127.0.0.1", server.getLocalPort(), null, null);
        client.connect();

        
        byte[] payload = {0x00, 0x01, 0x02, 0x03, 0x04};
        FramePacket original = new FramePacket(FrameType.I_FRAME, 42L, payload);
        client.send(original);

        
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
            FramePacket p = received.get();
            assertNotNull(p, "Server should have received the packet");
            assertEquals(FrameType.I_FRAME, p.getType());
            assertEquals(42L, p.getTimestamp());
            assertArrayEquals(payload, p.getPayload());
        });
    }

    @Test
    void broadcastFromServerToClient() throws Exception {
        AtomicReference<FramePacket> received = new AtomicReference<>();

        server = new RelayServer(0, null, null);
        server.start();

        client = new RelayClient("127.0.0.1", server.getLocalPort(), received::set, null);
        client.connect();

        
        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> server.getClientCount() == 1);

        
        byte[] payload = {(byte) 0xCA, (byte) 0xFE};
        FramePacket original = new FramePacket(FrameType.P_FRAME, 999L, payload);
        server.broadcast(original);

        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
            FramePacket p = received.get();
            assertNotNull(p);
            assertEquals(FrameType.P_FRAME, p.getType());
            assertEquals(999L, p.getTimestamp());
            assertArrayEquals(payload, p.getPayload());
        });
    }

    

    @Test
    void hundredPacketsInOrder() throws Exception {
        List<FramePacket> received = new CopyOnWriteArrayList<>();

        server = new RelayServer(0, received::add, null);
        server.start();

        client = new RelayClient("127.0.0.1", server.getLocalPort(), null, null);
        client.connect();

        int count = 100;
        for (int i = 0; i < count; i++) {
            
            byte[] payload = new byte[]{
                    (byte) (i >> 24),
                    (byte) (i >> 16),
                    (byte) (i >> 8),
                    (byte) i
            };
            FramePacket packet = new FramePacket(FrameType.P_FRAME, i, payload);
            client.send(packet);
        }

        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> received.size() == count);

        for (int i = 0; i < count; i++) {
            FramePacket p = received.get(i);
            assertEquals(i, p.getTimestamp(),
                    "Packet #" + i + " arrived out of order");

            
            byte[] payload = p.getPayload();
            int restored = ((payload[0] & 0xFF) << 24)
                    | ((payload[1] & 0xFF) << 16)
                    | ((payload[2] & 0xFF) << 8)
                    |  (payload[3] & 0xFF);
            assertEquals(i, restored,
                    "Payload of packet #" + i + " is corrupted");
        }
    }

    @Test
    void hundredBroadcastPackets() throws Exception {
        List<FramePacket> received = new CopyOnWriteArrayList<>();

        server = new RelayServer(0, null, null);
        server.start();

        client = new RelayClient("127.0.0.1", server.getLocalPort(), received::add, null);
        client.connect();

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> server.getClientCount() == 1);

        int count = 100;
        for (int i = 0; i < count; i++) {
            byte[] payload = new byte[64]; 
            payload[0] = (byte) i;
            server.broadcast(new FramePacket(FrameType.I_FRAME, i, payload));
        }

        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> received.size() == count);

        for (int i = 0; i < count; i++) {
            assertEquals(i, received.get(i).getTimestamp());
            assertEquals((byte) i, received.get(i).getPayload()[0]);
        }
    }

    

    @Test
    void broadcastToMultipleClients() throws Exception {
        List<FramePacket> received1 = new CopyOnWriteArrayList<>();
        List<FramePacket> received2 = new CopyOnWriteArrayList<>();

        server = new RelayServer(0, null, null);
        server.start();

        RelayClient client1 = new RelayClient(
                "127.0.0.1", server.getLocalPort(), received1::add, null);
        RelayClient client2 = new RelayClient(
                "127.0.0.1", server.getLocalPort(), received2::add, null);

        client1.connect();
        client2.connect();

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> server.getClientCount() == 2);

        byte[] payload = {0x42};
        server.broadcast(new FramePacket(FrameType.SPS, 7L, payload));

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> received1.size() == 1 && received2.size() == 1);

        assertArrayEquals(payload, received1.get(0).getPayload());
        assertArrayEquals(payload, received2.get(0).getPayload());

        client1.disconnect();
        client2.disconnect();
        
    }

    

    @Test
    void emptyPayloadTransfer() throws Exception {
        AtomicReference<FramePacket> received = new AtomicReference<>();

        server = new RelayServer(0, received::set, null);
        server.start();

        client = new RelayClient("127.0.0.1", server.getLocalPort(), null, null);
        client.connect();

        client.send(new FramePacket(FrameType.PPS, 0L, new byte[0]));

        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
            FramePacket p = received.get();
            assertNotNull(p);
            assertEquals(0, p.getPayloadLength());
            assertEquals(FrameType.PPS, p.getType());
        });
    }

    

    @Test
    void largePayloadTransfer() throws Exception {
        AtomicReference<FramePacket> received = new AtomicReference<>();

        server = new RelayServer(0, received::set, null);
        server.start();

        client = new RelayClient("127.0.0.1", server.getLocalPort(), null, null);
        client.connect();

        
        byte[] payload = new byte[512 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251); 
        }

        client.send(new FramePacket(FrameType.I_FRAME, 12345L, payload));

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
            FramePacket p = received.get();
            assertNotNull(p);
            assertArrayEquals(payload, p.getPayload());
        });
    }

    

    @Test
    void clientDisconnectNotifiesServer() throws Exception {
        AtomicBoolean disconnected = new AtomicBoolean(false);
        AtomicReference<String> disconnectedAddress = new AtomicReference<>();

        ConnectionListener serverListener = new ConnectionListener() {
            @Override
            public void onConnected(String remoteAddress) {}

            @Override
            public void onDisconnected(String remoteAddress, Throwable cause) {
                disconnectedAddress.set(remoteAddress);
                disconnected.set(true);
            }
        };

        server = new RelayServer(0, null, serverListener);
        server.start();

        client = new RelayClient("127.0.0.1", server.getLocalPort(), null, null);
        client.connect();

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> server.getClientCount() == 1);

        
        client.disconnect();
        client = null; 

        
        await().atMost(2, TimeUnit.SECONDS)
                .untilTrue(disconnected);

        assertNotNull(disconnectedAddress.get());

        
        
        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> server.getClientCount() == 0);
    }

    

    @Test
    void serverStopNotifiesClient() throws Exception {
        AtomicBoolean disconnected = new AtomicBoolean(false);
        AtomicReference<String> disconnectedAddress = new AtomicReference<>();

        server = new RelayServer(0, null, null);
        server.start();

        ConnectionListener clientListener = new ConnectionListener() {
            @Override
            public void onDisconnected(String remoteAddress, Throwable cause) {
                disconnectedAddress.set(remoteAddress);
                disconnected.set(true);
            }
        };

        client = new RelayClient("127.0.0.1", server.getLocalPort(), null, clientListener);
        client.connect();

        assertTrue(client.isConnected());

        
        server.stop();
        server = null; 

        
        await().atMost(5, TimeUnit.SECONDS)
                .untilTrue(disconnected);

        assertNotNull(disconnectedAddress.get());
    }

    

    @Test
    void connectionListenersCalledOnBothSides() throws Exception {
        CountDownLatch serverConnected = new CountDownLatch(1);
        CountDownLatch clientConnected = new CountDownLatch(1);

        ConnectionListener serverListener = new ConnectionListener() {
            @Override
            public void onConnected(String remoteAddress) {
                serverConnected.countDown();
            }
        };

        ConnectionListener clientListener = new ConnectionListener() {
            @Override
            public void onConnected(String remoteAddress) {
                clientConnected.countDown();
            }
        };

        server = new RelayServer(0, null, serverListener);
        server.start();

        client = new RelayClient("127.0.0.1", server.getLocalPort(), null, clientListener);
        client.connect();

        assertTrue(serverConnected.await(2, TimeUnit.SECONDS),
                "Server should have received onConnected");
        assertTrue(clientConnected.await(2, TimeUnit.SECONDS),
                "Client should have received onConnected");
    }

    

    @Test
    void connectToNonExistentServerThrows() {
        
        client = new RelayClient("127.0.0.1", 1, null, null);

        assertThrows(Exception.class, () -> client.connect());
    }

    

    @Test
    void allFrameTypesInSequence() throws Exception {
        List<FramePacket> received = new CopyOnWriteArrayList<>();

        server = new RelayServer(0, received::add, null);
        server.start();

        client = new RelayClient("127.0.0.1", server.getLocalPort(), null, null);
        client.connect();

        
        client.send(new FramePacket(FrameType.SPS, 0L, new byte[]{0x67, 0x42}));
        client.send(new FramePacket(FrameType.PPS, 0L, new byte[]{0x68, 0x01}));
        client.send(new FramePacket(FrameType.I_FRAME, 0L, new byte[]{0x65}));
        client.send(new FramePacket(FrameType.P_FRAME, 33L, new byte[]{0x41}));

        await().atMost(3, TimeUnit.SECONDS)
                .until(() -> received.size() == 4);

        assertEquals(FrameType.SPS, received.get(0).getType());
        assertEquals(FrameType.PPS, received.get(1).getType());
        assertEquals(FrameType.I_FRAME, received.get(2).getType());
        assertEquals(FrameType.P_FRAME, received.get(3).getType());
    }
}