/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.quic;

import io.netty.channel.ChannelHandler;
import io.netty.util.concurrent.ImmediateExecutor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.Executor;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class QuicCodecBuilderTest {

    @Test
    void testCopyConstructor() throws IllegalAccessException {
        TestQuicCodecBuilder original = new TestQuicCodecBuilder();
        init(original);
        TestQuicCodecBuilder copy = new TestQuicCodecBuilder(original);
        assertThat(copy).usingRecursiveComparison().isEqualTo(original);
    }

    private static void init(TestQuicCodecBuilder builder) throws IllegalAccessException {
        Field[] fields = builder.getClass().getSuperclass().getDeclaredFields();
        for (Field field : fields) {
            modifyField(builder, field);
        }
    }

    private static void modifyField(TestQuicCodecBuilder builder, Field field) throws IllegalAccessException {
        field.setAccessible(true);
        Class<?> clazz = field.getType();
        if (Boolean.class == clazz) {
            field.set(builder, Boolean.TRUE);
        } else if (Integer.class == clazz) {
            field.set(builder, Integer.MIN_VALUE);
        } else if (Long.class == clazz) {
            field.set(builder, Long.MIN_VALUE);
        } else if (QuicCongestionControlAlgorithm.class == clazz) {
            field.set(builder, QuicCongestionControlAlgorithm.CUBIC);
        } else if (FlushStrategy.class == clazz) {
            field.set(builder, FlushStrategy.afterNumBytes(10));
        } else if (Function.class == clazz) {
            field.set(builder, Function.identity());
        } else if (boolean.class == clazz) {
            field.setBoolean(builder, true);
        } else if (int.class == clazz) {
            field.setInt(builder, -1);
        } else if (byte[].class == clazz) {
            field.set(builder, new byte[16]);
        } else if (Executor.class == clazz) {
            field.set(builder, ImmediateExecutor.INSTANCE);
        } else {
            throw new IllegalArgumentException("Unknown field type " + clazz);
        }
    }

    private static final class TestQuicCodecBuilder extends QuicCodecBuilder<TestQuicCodecBuilder> {

        TestQuicCodecBuilder() {
            super(true);
        }

        TestQuicCodecBuilder(TestQuicCodecBuilder builder) {
            super(builder);
        }

        @Override
        public TestQuicCodecBuilder clone() {
            // no-op
            return null;
        }

        @Override
        protected ChannelHandler build(
                QuicheConfig config,
                Function<QuicChannel, ? extends QuicSslEngine> sslContextProvider,
                Executor sslTaskExecutor,
                int localConnIdLength,
                FlushStrategy flushStrategy) {
            // no-op
            return null;
        }
    }
}
