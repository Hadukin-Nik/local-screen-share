package ru.hniApplications.testApplication.discovery;


public interface DiscoveryListener {

    void onServiceFound(DiscoveredService service);

    void onServiceLost(DiscoveredService service);
}