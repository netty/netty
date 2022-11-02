package io.netty.nio.channel;

import org.junit.Test;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 11.09.2021
 */
public class ScatterTest {

    @Test
    public void testScattering() throws Exception {

        RandomAccessFile file = new RandomAccessFile("D:\\file01.txt", "rw");
        FileChannel channel = file.getChannel();

        ByteBuffer header = ByteBuffer.allocate(128);
        ByteBuffer body = ByteBuffer.allocate(1024);

        ByteBuffer[] buffers = {header, body};
        channel.read(buffers);
    }


    @Test
    public void testGathering() throws Exception {
        RandomAccessFile file = new RandomAccessFile("D:\\file01.txt", "rw");
        FileChannel channel = file.getChannel();

        ByteBuffer header = ByteBuffer.allocate(128);
        ByteBuffer body = ByteBuffer.allocate(1024);

        ByteBuffer[] buffers = {header, body};
        channel.write(buffers);
    }





















}
