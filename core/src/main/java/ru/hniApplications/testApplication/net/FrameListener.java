package ru.hniApplications.testApplication.net;

import ru.hniApplications.testApplication.FramePacket;


@FunctionalInterface
public interface FrameListener {

    void onFrame(FramePacket packet);
}
