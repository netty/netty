/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.jni.test;

import io.netty.jni.libaio.LibaioFile;
import io.netty.jni.libaio.LibaioContext;
import io.netty.jni.libaio.ErrorInfo;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This test is using a different package from {@link LibaioFile}
 * as I need to validate public methods on the API
 */
public class LibaioTest {

    /** This is just an arbitrary number for a number of elements you need to pass to the libaio init method
     * Some of the tests are using half of this number, so if anyone decide to change this please use an even number.
     */
    private static final int LIBAIO_QUEUE_SIZE = 50;

    @Rule
    public TemporaryFolder temporaryFolder;

    public LibaioContext control;

    @Before
    public void setUpFactory() {
        control = new LibaioContext(LIBAIO_QUEUE_SIZE);
    }

    @After
    public void deleteFactory() {
        control.close();
    }

    public LibaioTest() {
        /*
         *  I didn't use /tmp for three reasons
         *  - Most systems now will use tmpfs which is not compatible with O_DIRECT
         *  - This would fill up /tmp in case of failures.
         *  - target is cleaned up every time you do a mvn clean, so it's safer
         */
        File parent = new File("./target");
        parent.mkdirs();
        temporaryFolder = new TemporaryFolder(parent);
    }

    @Test
    public void testOpen() throws Exception {
        LibaioFile fileDescriptor = control.openFile(temporaryFolder.newFile("test.bin"), true);
        fileDescriptor.close();
    }

    @Test
    public void testSubmitWriteOnTwoFiles() throws Exception {

        File file1 = temporaryFolder.newFile("test.bin");
        File file2 = temporaryFolder.newFile("test2.bin");

        fillupFile(file1, LIBAIO_QUEUE_SIZE / 2);
        fillupFile(file2, LIBAIO_QUEUE_SIZE / 2);

        LibaioFile[] fileDescriptor = new LibaioFile[] {control.openFile(file1, true),
                                                        control.openFile(file2, true)};

        ByteBuffer buffer = control.newAlignedBuffer(512, 512);

        try {
            for (int i = 0; i < 512; i++) {
                buffer.put((byte) 'a');
            }

            Object callback = new Object();
            Object[] callbacks = new Object[LIBAIO_QUEUE_SIZE];

            for (int i = 0; i < LIBAIO_QUEUE_SIZE / 2; i++) {
                for (LibaioFile file : fileDescriptor) {
                    Assert.assertTrue(file.write(i * 512, 512, buffer, callback));
                }
            }

            Assert.assertEquals(LIBAIO_QUEUE_SIZE, control.poll(callbacks, LIBAIO_QUEUE_SIZE, LIBAIO_QUEUE_SIZE));

            for (Object returnedCallback : callbacks) {
                Assert.assertSame(returnedCallback, callback);
            }

            for (LibaioFile file : fileDescriptor) {
                ByteBuffer bigbuffer = LibaioContext.newAlignedBuffer(512 * 25, 512);
                file.read(0, 512 * 25, bigbuffer, callback);
                Assert.assertEquals(1, control.poll(callbacks, 1, LIBAIO_QUEUE_SIZE));

                for (Object returnedCallback : callbacks) {
                    Assert.assertSame(returnedCallback, callback);
                }

                for (int i = 0; i < 512 * 25; i++) {
                    Assert.assertEquals((byte) 'a', bigbuffer.get());
                }

                LibaioContext.freeBuffer(bigbuffer);

                file.close();
            }
        } finally {
            LibaioContext.freeBuffer(buffer);
        }
    }

    @Test
    public void testSubmitWriteAndRead() throws Exception {
        Object callback = new Object();

        Object[] callbacks = new Object[LIBAIO_QUEUE_SIZE];

        LibaioFile fileDescriptor = control.openFile(temporaryFolder.newFile("test.bin"), true);

        // ByteBuffer buffer = ByteBuffer.allocateDirect(512);
        ByteBuffer buffer = control.newAlignedBuffer(512, 512);

        try {
            for (int i = 0; i < 512; i++) {
                buffer.put((byte) 'a');
            }

            buffer.rewind();

            Assert.assertTrue(fileDescriptor.write(0, 512, buffer, callback));

            int retValue = control.poll(callbacks, 1, LIBAIO_QUEUE_SIZE);
            Assert.assertEquals(1, retValue);

            Assert.assertSame(callback, callbacks[0]);

            LibaioContext.freeBuffer(buffer);

            buffer = LibaioContext.newAlignedBuffer(512, 512);

            for (int i = 0; i < 512; i++) {
                buffer.put((byte) 'B');
            }

            Assert.assertTrue(fileDescriptor.write(0, 512, buffer, null));

            Assert.assertEquals(1, control.poll(callbacks, 1, LIBAIO_QUEUE_SIZE));

            buffer.rewind();

            Assert.assertTrue(fileDescriptor.read(0, 512, buffer, null));

            Assert.assertEquals(1, control.poll(callbacks, 1, LIBAIO_QUEUE_SIZE));

            for (int i = 0; i < 512; i++) {
                Assert.assertEquals('B', buffer.get());
            }
        } finally {
            LibaioContext.freeBuffer(buffer);
            fileDescriptor.close();
        }
    }

    @Test
    /**
     * This file is making use of libaio without O_DIRECT
     * We won't need special buffers on this case.
     */
    public void testSubmitWriteAndReadRegularBuffers() throws Exception {
        Object callback = new Object();

        Object[] callbacks = new Object[LIBAIO_QUEUE_SIZE];

        File file = temporaryFolder.newFile("test.bin");

        fillupFile(file, LIBAIO_QUEUE_SIZE);

        LibaioFile fileDescriptor = control.openFile(file, false);

        final int BUFFER_SIZE = 50;

        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

        try {
            for (int i = 0; i < BUFFER_SIZE; i++) {
                buffer.put((byte) 'a');
            }

            buffer.rewind();

            Assert.assertTrue(fileDescriptor.write(0, BUFFER_SIZE, buffer, callback));

            int retValue = control.poll(callbacks, 1, LIBAIO_QUEUE_SIZE);
            System.out.println("Return from poll::" + retValue);
            Assert.assertEquals(1, retValue);

            Assert.assertSame(callback, callbacks[0]);

            buffer.rewind();

            for (int i = 0; i < BUFFER_SIZE; i++) {
                buffer.put((byte) 'B');
            }

            Assert.assertTrue(fileDescriptor.write(0, BUFFER_SIZE, buffer, null));

            Assert.assertEquals(1, control.poll(callbacks, 1, LIBAIO_QUEUE_SIZE));

            buffer.rewind();

            Assert.assertTrue(fileDescriptor.read(0, 50, buffer, null));

            Assert.assertEquals(1, control.poll(callbacks, 1, LIBAIO_QUEUE_SIZE));

            for (int i = 0; i < BUFFER_SIZE; i++) {
                Assert.assertEquals('B', buffer.get());
            }
        } finally {
            fileDescriptor.close();
        }
    }

    @Test
    public void testSubmitRead() throws Exception {

        Object callback = new Object();

        Object[] callbacks = new Object[LIBAIO_QUEUE_SIZE];

        File file = temporaryFolder.newFile("test.bin");

        fillupFile(file, LIBAIO_QUEUE_SIZE);

        LibaioFile fileDescriptor = control.openFile(file, true);

        ByteBuffer buffer = LibaioContext.newAlignedBuffer(512, 512);

        final int BUFFER_SIZE = 512;
        try {
            for (int i = 0; i < BUFFER_SIZE; i++) {
                buffer.put((byte) '@');
            }

            Assert.assertTrue(fileDescriptor.write(0, BUFFER_SIZE, buffer, callback));
            Assert.assertEquals(1, control.poll(callbacks, 1, LIBAIO_QUEUE_SIZE));
            Assert.assertSame(callback, callbacks[0]);

            buffer.rewind();

            Assert.assertTrue(fileDescriptor.read(0, BUFFER_SIZE, buffer, callback));

            Assert.assertEquals(1, control.poll(callbacks, 1, LIBAIO_QUEUE_SIZE));

            Assert.assertSame(callback, callbacks[0]);

            for (int i = 0; i < BUFFER_SIZE; i++) {
                Assert.assertEquals('@', buffer.get());
            }
        } finally {
            LibaioContext.freeBuffer(buffer);
            fileDescriptor.close();
        }
    }

    @Test
    public void testInvalidWrite() throws Exception {

        CountClass callback = new CountClass();

        Object[] callbacks = new Object[LIBAIO_QUEUE_SIZE];

        File file = temporaryFolder.newFile("test.bin");

        fillupFile(file, LIBAIO_QUEUE_SIZE);

        LibaioFile fileDescriptor = control.openFile(file, true);

        try {
            ByteBuffer buffer = ByteBuffer.allocateDirect(300);
            for (int i = 0; i < 300; i++) {
                buffer.put((byte) 'z');
            }

            Assert.assertTrue(fileDescriptor.write(0, 300, buffer, callback));

            Assert.assertEquals(1, control.poll(callbacks, 1, LIBAIO_QUEUE_SIZE));

            Assert.assertTrue(callbacks[0] instanceof ErrorInfo);

            // Error condition
            Assert.assertSame(((ErrorInfo) callbacks[0]).callback(), callback);

            System.out.println("Error:" + callbacks[0]);

            buffer = fileDescriptor.newBuffer(512);
            for (int i = 0; i < 512; i++) {
                buffer.put((byte) 'z');
            }

            Assert.assertTrue(fileDescriptor.write(0, 512, buffer, callback));

            Assert.assertEquals(1, control.poll(callbacks, 1, 1));

            Assert.assertSame(callback, callbacks[0]);

            Assert.assertTrue(fileDescriptor.write(5, 512, buffer, callback));

            Assert.assertEquals(1, control.poll(callbacks, 1, 1));

            Assert.assertTrue(callbacks[0] instanceof ErrorInfo);

            callbacks = null;
            callback = null;

            CountClass.checkLeaks();
        } finally {
            fileDescriptor.close();
        }
    }

    @Test
    public void testLeaks() throws Exception {
        File file = temporaryFolder.newFile("test.bin");

        fillupFile(file, LIBAIO_QUEUE_SIZE * 2);

        Object[] callbacks = new Object[LIBAIO_QUEUE_SIZE];

        LibaioFile fileDescriptor = control.openFile(file, true);

        ByteBuffer bufferWrite = LibaioContext.newAlignedBuffer(512, 512);

        try {
            for (int i = 0; i < 512; i++) {
                bufferWrite.put((byte) 'B');
            }

            for (int j = 0; j < LIBAIO_QUEUE_SIZE * 2; j++) {
                for (int i = 0; i < LIBAIO_QUEUE_SIZE; i++) {
                    CountClass countClass = new CountClass();
                    Assert.assertTrue(fileDescriptor.write(i * 512, 512, bufferWrite, countClass));
                }

                Assert.assertEquals(LIBAIO_QUEUE_SIZE, control.poll(callbacks, LIBAIO_QUEUE_SIZE, LIBAIO_QUEUE_SIZE));

                for (int i = 0; i < LIBAIO_QUEUE_SIZE; i++) {
                    Assert.assertNotNull(callbacks[i]);
                    callbacks[i] = null;
                }
            }

            CountClass.checkLeaks();
        } finally {
            LibaioContext.freeBuffer(bufferWrite);
        }
    }

    @Test
    public void testIOExceptionConditions() throws Exception {
        boolean exceptionThrown = false;

        try {
            // There is no space for a queue this huge, the native layer should throw the exception
            LibaioContext newController = new LibaioContext(1000000);
        } catch (RuntimeException e) {
            exceptionThrown = true;
        }

        Assert.assertTrue(exceptionThrown);
        exceptionThrown = false;

        try {
            // this should throw an exception, we shouldn't be able to open a directory!
            control.openFile(temporaryFolder.getRoot(), true);
        } catch (IOException expected) {
            exceptionThrown = true;
        }

        Assert.assertTrue(exceptionThrown);

        exceptionThrown = false;

        LibaioFile fileDescriptor = control.openFile(temporaryFolder.newFile(), true);
        fileDescriptor.close();
        try {
            fileDescriptor.close();
        } catch (IOException expected) {
            exceptionThrown = true;
        }

        Assert.assertTrue(exceptionThrown);

        fileDescriptor = control.openFile(temporaryFolder.newFile(), true);

        ByteBuffer buffer = fileDescriptor.newBuffer(512);

        try {
            for (int i = 0; i < 512; i++) {
                buffer.put((byte) 'a');
            }

            for (int i = 0; i < LIBAIO_QUEUE_SIZE; i++) {
                Assert.assertTrue(fileDescriptor.write(i * 512, 512, buffer, new CountClass()));
            }

            Assert.assertFalse(fileDescriptor.write(0, 512, buffer, new CountClass()));

            Object[] callbacks = new Object[LIBAIO_QUEUE_SIZE];
            Assert.assertEquals(LIBAIO_QUEUE_SIZE, control.poll(callbacks, LIBAIO_QUEUE_SIZE, LIBAIO_QUEUE_SIZE));

            // it should be possible to write now after queue space being released
            Assert.assertTrue(fileDescriptor.write(0, 512, buffer, new CountClass()));
            Assert.assertEquals(1, control.poll(callbacks, 1, 100));

            CountClass errorCallback = new CountClass();
            // odd positions will have failures through O_DIRECT
            Assert.assertTrue(fileDescriptor.read(3, 512, buffer, errorCallback));
            Assert.assertEquals(1, control.poll(callbacks, 1, 50));
            Assert.assertTrue(callbacks[0] instanceof ErrorInfo);
            Assert.assertSame(errorCallback, ((ErrorInfo) callbacks[0]).callback());

            // to help GC and the checkLeaks
            callbacks = null;
            errorCallback = null;

            CountClass.checkLeaks();

            exceptionThrown = false;
            try {
                LibaioContext.newAlignedBuffer(300, 512);
            } catch (RuntimeException e) {
                exceptionThrown = true;
            }

            Assert.assertTrue(exceptionThrown);

            exceptionThrown = false;
            try {
                LibaioContext.newAlignedBuffer(-512, 512);
            } catch (RuntimeException e) {
                exceptionThrown = true;
            }

            Assert.assertTrue(exceptionThrown);
        } finally {
            LibaioContext.freeBuffer(buffer);
        }
    }

    private void fillupFile(File file, int blocks) throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        byte[] bufferWrite = new byte[512];
        for (int i = 0; i < 512; i++) {
            bufferWrite[i] = (byte) 0;
        }

        for (int i = 0; i < blocks; i++) {
            fileOutputStream.write(bufferWrite);
        }

        fileOutputStream.close();
    }

    static class CountClass {
        static AtomicInteger count = new AtomicInteger();

        public CountClass() {
            count.incrementAndGet();
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            count.decrementAndGet();
        }

        public static void checkLeaks() throws InterruptedException {
            for (int i = 0; count.get() != 0 && i < 50; i++) {
                WeakReference reference = new WeakReference(new Object());
                while (reference.get() != null) {
                    System.gc();
                    Thread.sleep(100);
                }
            }
            Assert.assertEquals(0, count.get());
        }
    }
}
