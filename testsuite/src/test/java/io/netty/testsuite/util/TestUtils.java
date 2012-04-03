package io.netty.testsuite.util;

import java.io.IOException;
import java.net.ServerSocket;

public class TestUtils {

    private final static int START_PORT = 20000;
    private final static int END_PORT = 30000;
    
    /**
     * Return a free port which can be used to bind to
     * 
     * @return port
     */
    public static int getFreePort() {
        for(int start = START_PORT; start <= END_PORT; start++) {
            try {
                ServerSocket socket = new ServerSocket(start);
                socket.setReuseAddress(true);
                socket.close();
                return start;
            } catch (IOException e) {
                // ignore 
            }
            
        }
        throw new RuntimeException("Unable to find a free port....");
    }
}