package io.netty.nio.filelock;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * @author lxcecho 909231497@qq.com
 * @since 15.09.2021
 */
public class FileLocalTest {

    @Test
    public void testFileLock() throws Exception {
        /**
         * lock(): 对整个文件加锁，默认为排他锁。
         * lock(long position, long size, boolean shared): 自定义加锁方式
         *  前两个参数指定要加锁的部分【可以只对此文件的部分内容加锁】，第三个参数值指定是否共享锁。
         *
         * tryLock(): 对整个文件加锁，默认为排他锁。
         * tryLock(long position, long size, boolean shared): 自定义加锁方式
         *  如果指定为共享锁，则其他进程可以读此文件，所有进程均不能写此文件，如果某进程试图对此文件进行写操作，会抛出异常。
         *
         * lock()  和 tryLock() 区别：
         *  lock 是阻塞式的，如果未获取到文件锁，会一直阻塞当前线程，直到获取文件锁。
         *  tryLock 和 lock 作用相同，只不过 tryLock 是非阻塞式的，tryLock 是尝试获取文件锁，获取成功就返回锁对象，否则返回 null，不会阻塞当前线程。
         */

        String input = "lxcecho";
        System.out.println("input : " + input);
        ByteBuffer buffer = ByteBuffer.wrap(input.getBytes());

        String filePath = "D:\\file01.txt";
        Path path = Paths.get(filePath);

        FileChannel fileChannel = FileChannel.
                open(path, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        fileChannel.position(fileChannel.size() - 1);

        // lock
        FileLock lock = fileChannel.lock(0L, Long.MAX_VALUE, false);
        System.out.println("是否共享锁 ： " + lock.isShared());

        fileChannel.write(buffer);
        fileChannel.close();

        System.out.println("写操作完成");

        // read file
        readFile(filePath);

    }

    private void readFile(String filePath) throws Exception {
        FileReader fileReader = new FileReader(filePath);
        BufferedReader bufferedReader = new BufferedReader(fileReader);

        String str = bufferedReader.readLine();
        System.out.println("读出的内容是:");
        while(str != null){
            System.out.println(str);
            str = bufferedReader.readLine();
        }

        fileReader.close();
        bufferedReader.close();
    }

}
