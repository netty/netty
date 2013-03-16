/*******************************************************************************
 * Copyright (c) 2012-2013 HFS Project. All Rights Reserved.
 *
 * This software is the proprietary information of Ursa-HFS Project.
 * Use is subject to license terms.
 ******************************************************************************/

package io.netty.handler.codec.http.multipart;

import io.netty.buffer.ByteBuf;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;

import static io.netty.util.CharsetUtil.UTF_8;
import static org.junit.Assert.*;

/** {@link AbstractMemoryHttpData} test cases. */
public class AbstractMemoryHttpDataTest {
    /**
     * Provide content into HTTP data with input stream.
     *
     * @throws Exception In case of any exception.
     */
    @Test
    public void testSetContentFromStream() throws Exception {
        Random random = new SecureRandom();

        for (int i = 0; i < 20; i++) {
            // Generate input data bytes.
            int size = random.nextInt(Short.MAX_VALUE);
            byte[] bytes = new byte[size];

            random.nextBytes(bytes);

            // Generate parsed HTTP data block.
            TestHttpData data = new TestHttpData("name", UTF_8, 0);

            data.setContent(new ByteArrayInputStream(bytes));

            // Validate stored data.
            ByteBuf buffer = data.getByteBuf();

            assertEquals(0, buffer.readerIndex());
            assertEquals(bytes.length, buffer.writerIndex());
            assertArrayEquals(bytes, Arrays.copyOf(buffer.array(), bytes.length));
        }
    }


    /** Memory-based HTTP data implementation for test purposes. */
    private static final class TestHttpData extends AbstractMemoryHttpData {
        /**
         * Constructs HTTP data for tests.
         *
         * @param name    Name of parsed data block.
         * @param charset Used charset for data decoding.
         * @param size    Expected data block size.
         */
        protected TestHttpData(String name, Charset charset, long size) {
            super(name, charset, size);
        }

        public InterfaceHttpData.HttpDataType getHttpDataType() {
            throw new UnsupportedOperationException("Should never be called.");
        }

        @Override
        public HttpData copy() {
            throw new UnsupportedOperationException("Should never be called.");
        }

        public int compareTo(InterfaceHttpData o) {
            throw new UnsupportedOperationException("Should never be called.");
        }
    }
}
