package com.ascode.android.debugger.jdwp.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.junit.Test;

import com.ascode.android.debugger.jdwp.JdwpDebugSession;
import com.ascode.android.debugger.jdwp.JdwpSessionConfig;
import com.ascode.android.debugger.jdwp.JdwpSessionState;

public class RealJdwpBridgeTest {

    @Test
    public void start_withReachableSocket_attachesAndStops() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Thread acceptThread = new Thread(() -> {
                try (Socket accepted = serverSocket.accept()) {
                    // Keep socket open for the duration of the test session.
                    while (!Thread.currentThread().isInterrupted() && !accepted.isClosed()) {
                        Thread.sleep(10L);
                    }
                } catch (Exception ignored) {
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();

            RealJdwpBridge bridge = new RealJdwpBridge();
            JdwpDebugSession session = bridge.createSession(
                    JdwpSessionConfig.local("project-runtime", "com.ascode.android", serverSocket.getLocalPort())
            );

            boolean started = session.start();
            assertTrue(started);
            assertEquals(JdwpSessionState.ATTACHED, session.state());

            boolean stopped = session.stop();
            assertTrue(stopped);
            assertEquals(JdwpSessionState.TERMINATED, session.state());

            acceptThread.interrupt();
            acceptThread.join(300L);
        }
    }

    @Test
    public void start_withClosedPort_failsGracefully() throws IOException {
        int unusedPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            unusedPort = probe.getLocalPort();
        }

        RealJdwpBridge bridge = new RealJdwpBridge();
        JdwpDebugSession session = bridge.createSession(
                JdwpSessionConfig.local("project-runtime", "com.ascode.android", unusedPort)
        );

        boolean started = session.start();
        assertFalse(started);
        assertEquals(JdwpSessionState.FAILED, session.state());
    }
}
