/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefensiveDecompressorTest {

    @Test
    public void enforcesNeedInputState() throws DecompressionException {
        TestDecompressor delegate = new TestDecompressor();
        DefensiveDecompressor decompressor = new DefensiveDecompressor(delegate);
        ByteBuf input = Unpooled.buffer(1).writeByte(1);

        assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
        decompressor.addInput(input);

        assertSame(input, delegate.input);
        assertTrue(delegate.addInputCalled);
        input.release();
        decompressor.close();
    }

    @Test
    public void enforcesNeedOutputState() throws DecompressionException {
        TestDecompressor delegate = new TestDecompressor();
        delegate.status = Decompressor.Status.NEED_OUTPUT;
        ByteBuf output = Unpooled.buffer(1).writeByte(1);
        delegate.output = output;
        DefensiveDecompressor decompressor = new DefensiveDecompressor(delegate);

        assertEquals(Decompressor.Status.NEED_OUTPUT, decompressor.status());
        assertSame(output, decompressor.takeOutput());
        assertTrue(delegate.takeOutputCalled);
        output.release();
        decompressor.close();
    }

    @Test
    public void rejectsCallsInWrongState() throws DecompressionException {
        TestDecompressor delegate = new TestDecompressor();
        DefensiveDecompressor decompressor = new DefensiveDecompressor(delegate);
        ByteBuf input = Unpooled.buffer(1).writeByte(1);

        assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
        assertThrows(IllegalStateException.class, decompressor::takeOutput);

        delegate.status = Decompressor.Status.NEED_OUTPUT;
        assertEquals(Decompressor.Status.NEED_OUTPUT, decompressor.status());
        assertThrows(IllegalStateException.class, () -> decompressor.addInput(input));

        decompressor.close();
    }

    @Test
    public void marksFailedWhenStatusThrows() {
        TestDecompressor delegate = new TestDecompressor();
        delegate.statusException = new DecompressionException("status failed");
        DefensiveDecompressor decompressor = new DefensiveDecompressor(delegate);

        assertSame(delegate.statusException, assertThrows(DecompressionException.class, decompressor::status));
        assertThrows(IllegalStateException.class, decompressor::status);
        decompressor.close();
    }

    @Test
    public void marksFailedWhenOperationThrows() throws DecompressionException {
        TestDecompressor delegate = new TestDecompressor();
        delegate.addInputException = new DecompressionException("addInput failed");
        DefensiveDecompressor decompressor = new DefensiveDecompressor(delegate);
        ByteBuf input = Unpooled.buffer(1).writeByte(1);

        assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
        assertSame(delegate.addInputException, assertThrows(DecompressionException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        decompressor.addInput(input);
                    }
                }));
        assertThrows(IllegalStateException.class, decompressor::status);

        input.release();
        decompressor.close();
    }

    @Test
    public void closeIdempotent() {
        TestDecompressor delegate = new TestDecompressor();
        DefensiveDecompressor decompressor = new DefensiveDecompressor(delegate);

        decompressor.close();

        assertTrue(delegate.closeCalled);
        assertThrows(IllegalStateException.class, decompressor::status);

        assertDoesNotThrow(decompressor::close);
    }

    private static final class TestDecompressor implements Decompressor {
        Decompressor.Status status = Decompressor.Status.NEED_INPUT;
        DecompressionException statusException;
        DecompressionException addInputException;
        ByteBuf input;
        ByteBuf output;
        boolean addInputCalled;
        boolean takeOutputCalled;
        boolean closeCalled;

        @Override
        public Status status() throws DecompressionException {
            if (statusException != null) {
                throw statusException;
            }
            return status;
        }

        @Override
        public void addInput(ByteBuf buf) throws DecompressionException {
            addInputCalled = true;
            if (addInputException != null) {
                throw addInputException;
            }
            input = buf;
        }

        @Override
        public void endOfInput() {
        }

        @Override
        public ByteBuf takeOutput() {
            takeOutputCalled = true;
            return output;
        }

        @Override
        public void close() {
            closeCalled = true;
        }
    }
}
