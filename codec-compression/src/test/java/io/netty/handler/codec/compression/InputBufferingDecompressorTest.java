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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InputBufferingDecompressorTest {

    @Test
    public void releasesEmptyInput() throws DecompressionException {
        TestInputBufferingDecompressor decompressor = new TestInputBufferingDecompressor();
        ByteBuf input = Unpooled.buffer(0);

        decompressor.addInput(input);

        assertEquals(0, input.refCnt());
        assertEquals(0, decompressor.processInputCalls);
    }

    @Test
    public void buffersUnreadInputUntilClose() throws DecompressionException {
        TestInputBufferingDecompressor decompressor = new TestInputBufferingDecompressor();
        ByteBuf input = Unpooled.buffer(2).writeByte(1).writeByte(2);

        decompressor.addInput(input);

        assertEquals(1, decompressor.processInputCalls);
        assertEquals(1, decompressor.available());
        assertEquals(1, input.refCnt());

        decompressor.close();

        assertEquals(0, input.refCnt());
    }

    @Test
    public void releasesInputWhenProcessingFails() {
        TestInputBufferingDecompressor decompressor = new TestInputBufferingDecompressor();
        decompressor.processInputException = new DecompressionException("input failed");
        ByteBuf input = Unpooled.buffer(1).writeByte(1);

        assertSame(decompressor.processInputException,
                assertThrows(DecompressionException.class, new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        decompressor.addInput(input);
                    }
                }));
        assertEquals(0, input.refCnt());
    }

    @Test
    public void takeOutputConsumesBufferedInput() throws DecompressionException {
        TestInputBufferingDecompressor decompressor = new TestInputBufferingDecompressor();
        ByteBuf input = Unpooled.buffer(2).writeByte(1).writeByte(2);

        decompressor.addInput(input);
        ByteBuf output = decompressor.takeOutput();

        assertEquals(1, output.readableBytes());
        assertEquals(2, output.readByte());
        assertEquals(0, decompressor.available());
        assertEquals(0, input.refCnt());

        output.release();
    }

    @Test
    public void takeOutputReleasesOutputWhenStatusFails() throws DecompressionException {
        TestInputBufferingDecompressor decompressor = new TestInputBufferingDecompressor();
        decompressor.statusException = new DecompressionException("status failed");
        ByteBuf input = Unpooled.buffer(2).writeByte(1).writeByte(2);

        decompressor.addInput(input);

        assertSame(decompressor.statusException,
                assertThrows(DecompressionException.class, decompressor::takeOutput));
        assertEquals(0, decompressor.lastOutput.refCnt());
        decompressor.close();
    }

    @Test
    public void closeClearsCumulation() throws DecompressionException {
        TestInputBufferingDecompressor decompressor = new TestInputBufferingDecompressor();
        ByteBuf input = Unpooled.buffer(2).writeByte(1).writeByte(2);

        decompressor.addInput(input);
        decompressor.close();
        decompressor.close();

        assertEquals(0, input.refCnt());
    }

    private static final class TestInputBufferingDecompressor extends InputBufferingDecompressor {
        int processInputCalls;
        DecompressionException processInputException;
        DecompressionException statusException;
        ByteBuf lastOutput;

        TestInputBufferingDecompressor() {
            super(ByteBufAllocator.DEFAULT);
        }

        @Override
        void processInput(ByteBuf buf) throws DecompressionException {
            processInputCalls++;
            if (processInputException != null) {
                throw processInputException;
            }
            if (buf.isReadable()) {
                buf.readByte();
            }
        }

        @Override
        ByteBuf processOutput(ByteBuf buf) {
            ByteBuf output = allocator.buffer(buf.readableBytes());
            output.writeBytes(buf);
            lastOutput = output;
            return output;
        }

        @Override
        public void endOfInput() {
        }

        @Override
        public Status status() throws DecompressionException {
            if (statusException != null) {
                throw statusException;
            }
            return available() == 0 ? Status.NEED_INPUT : Status.NEED_OUTPUT;
        }
    }
}
