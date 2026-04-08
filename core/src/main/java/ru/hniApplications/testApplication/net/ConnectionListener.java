package ru.hniApplications.testApplication.net;

import io.netty.channel.Channel;


public interface ConnectionListener {

    
    default void onConnected(String remoteAddress) {}

    
    default void onDisconnected(String remoteAddress, Throwable cause) {}
}