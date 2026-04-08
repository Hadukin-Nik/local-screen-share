package ru.hniApplications.testApplication.discovery;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import java.io.IOException;
import java.net.InetAddress;


public class ServiceDiscovery {

    private JmDNS jmdns;
    private ServiceListener jmdnsListener;

    
    public void startListening(DiscoveryListener listener) throws IOException {
        InetAddress address = InetAddress.getLocalHost();
        jmdns = JmDNS.create(address);
        attachListener(listener);
    }

    
    public void startListening(DiscoveryListener listener, InetAddress bindAddress) throws IOException {
        jmdns = JmDNS.create(bindAddress);
        attachListener(listener);
    }

    private void attachListener(DiscoveryListener listener) {
        jmdnsListener = new ServiceListener() {
            @Override
            public void serviceAdded(ServiceEvent event) {
                
                
                event.getDNS().requestServiceInfo(event.getType(), event.getName());
            }

            @Override
            public void serviceResolved(ServiceEvent event) {
                DiscoveredService service = toDiscoveredService(event);
                if (service != null) {
                    listener.onServiceFound(service);
                }
            }

            @Override
            public void serviceRemoved(ServiceEvent event) {
                DiscoveredService service = toDiscoveredService(event);
                if (service != null) {
                    listener.onServiceLost(service);
                }
            }
        };

        jmdns.addServiceListener(ServiceAdvertiser.SERVICE_TYPE, jmdnsListener);
    }

    
    public void stopListening() {
        if (jmdns != null) {
            if (jmdnsListener != null) {
                jmdns.removeServiceListener(ServiceAdvertiser.SERVICE_TYPE, jmdnsListener);
                jmdnsListener = null;
            }
            try {
                jmdns.close();
            } catch (IOException ignored) {
            }
            jmdns = null;
        }
    }

    private static DiscoveredService toDiscoveredService(ServiceEvent event) {
        String[] addresses = event.getInfo() != null
                ? event.getInfo().getHostAddresses()
                : null;

        String host = (addresses != null && addresses.length > 0)
                ? addresses[0]
                : null;

        int port = event.getInfo() != null
                ? event.getInfo().getPort()
                : 0;

        String name = event.getName();

        
        if (host == null || port == 0) {
            return null;
        }

        return new DiscoveredService(host, port, name);
    }
}