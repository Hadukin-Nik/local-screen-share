package ru.hniApplications.testApplication.session;

import ru.hniApplications.testApplication.discovery.DiscoveredService;

import java.util.Optional;


public class SessionResult {

    private final boolean success;
    private final String message;
    private final DiscoveredService existingService;

    private SessionResult(boolean success, String message, DiscoveredService existingService) {
        this.success = success;
        this.message = message;
        this.existingService = existingService;
    }

    public static SessionResult ok() {
        return new SessionResult(true, "Broadcast started", null);
    }

    public static SessionResult error(String message) {
        return new SessionResult(false, message, null);
    }

    public static SessionResult alreadyBroadcasting(DiscoveredService existing) {
        String msg = String.format(
                "A broadcast is already running on the network: device '%s' (%s:%d)",
                existing.getDeviceName(),
                existing.getHost(),
                existing.getPort()
        );
        return new SessionResult(false, msg, existing);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Optional<DiscoveredService> getExistingService() {
        return Optional.ofNullable(existingService);
    }

    @Override
    public String toString() {
        return "SessionResult{success=" + success + ", message='" + message + "'}";
    }
}