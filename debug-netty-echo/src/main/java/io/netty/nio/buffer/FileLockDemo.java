package io.netty.nio.buffer;

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/**
 * 文件共享锁和排他锁
 *
 * @author lxcecho 909231497@qq.com
 * @since 22:30 13-11-2022
 */
public class FileLockDemo {

    public static void main(String[] args) throws Exception {
        RandomAccessFile file = new RandomAccessFile("lxcecho.txt", "rw");
        FileChannel channel = file.getChannel();

        FileLock lock = channel.lock(3, 6, true);
        System.out.println("valid: " + lock.isValid() + " lockType: " + lock.isShared());

        lock.release();
        file.close();
    }

}
