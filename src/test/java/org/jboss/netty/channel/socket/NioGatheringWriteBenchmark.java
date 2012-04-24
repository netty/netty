package org.jboss.netty.channel.socket;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

public class NioGatheringWriteBenchmark {
    
    final static byte[] first = new byte[1024];
    final static byte[] second = new byte[1024 * 10];

    final static ChannelBuffer firstDirect = ChannelBuffers.directBuffer(first.length);
    final static ChannelBuffer secondDirect = ChannelBuffers.directBuffer(second.length);


    
    static {
        for (int i = 0; i < first.length; i++) {
            first[i] = (byte) i;
        }
        
        for (int i = 0; i < second.length; i++) {
            second[i] = (byte) i;
        }
        
        firstDirect.writeBytes(first);
        secondDirect.writeBytes(second);
    }
    
    final static ChannelBuffer firstHeap = ChannelBuffers.wrappedBuffer(second);
    final static ChannelBuffer secondHeap = ChannelBuffers.wrappedBuffer(second);

    public static void main(String args[]) throws IOException {
        if (args.length != 2) {
            System.err.println("Give argument direct|heap|mixed $rounds");
            System.exit(1);
            
        }
        final ServerSocket socket = new ServerSocket();
        socket.bind(new InetSocketAddress(0));

        Thread serverThread = new Thread(new Runnable() {
            public void run() {
                while(!Thread.interrupted()) {
                    try {
                        final Socket acceptedSocket = socket.accept();
                        new Thread(new Runnable() {
                            
                            public void run() {
                                InputStream in = null;
                                try {
                                    in = acceptedSocket.getInputStream();
                                    int i = 0;
                                    
                                    while ((i = in.read()) != -1) {
                                        //System.out.print(i);
                                    }
                                } catch (IOException e) {
                                    if (in != null) {
                                        try {
                                            in.close();
                                        } catch (IOException e1) {
                                            // ignore
                                        }
                                    }
                                }
                            }
                        }).start();
                    } catch (IOException e) {
                        // ignore
                    }

                   
                }
            } 
        });
        serverThread.start();
        
        ClientBootstrap cb = new ClientBootstrap(new NioClientSocketChannelFactory());
        ChannelFuture future = cb.connect(socket.getLocalSocketAddress());
        assertTrue(future.awaitUninterruptibly().isSuccess());
        Channel channel = future.getChannel();
        
        
        ChannelFuture f = null;
        long start = System.currentTimeMillis();
        
        ChannelBuffer firstBuf;
        ChannelBuffer secondBuf;
        
        String type = args[0];
        if (type.equalsIgnoreCase("direct")) {
            firstBuf = firstDirect;
            secondBuf = secondDirect;
        } else if (type.equalsIgnoreCase("heap")) {
            firstBuf = firstHeap;
            secondBuf = secondHeap;
        } else if (type.equalsIgnoreCase("mixed")) {
            firstBuf = firstDirect;
            secondBuf = secondHeap;
        } else {
            throw new IllegalArgumentException("Use direct|heap|mixed as arguments");
        }
        
        int rounds = Integer.parseInt(args[1]);
        
        for (int i = 0; i < rounds; i++) {
            f = channel.write(ChannelBuffers.wrappedBuffer(firstBuf.duplicate(), secondBuf.duplicate()));
        }
        assertTrue(f.awaitUninterruptibly().isSuccess());
        long stop = System.currentTimeMillis() - start;
        ChannelFuture cf = channel.close();
        assertTrue(cf.awaitUninterruptibly().isSuccess());
        socket.close();
        serverThread.interrupt();
        
        System.out.println("Execute " + rounds + " in " + stop + "ms");


    }

}
