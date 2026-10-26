/*
 * Copyright 2013 The Netty Project
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
package io.netty.buffer;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ReadOnlyUnsafeDirectByteBufferBufTest extends ReadOnlyDirectByteBufferBufTest {

    /**
     * Needs unsafe to run
     */
    @BeforeAll
    public static void assumeConditions() {
        assumeTrue(PlatformDependent.hasUnsafe(), "sun.misc.Unsafe not found, skip tests");
    }

    @Override
    protected ByteBuf buffer(ByteBuffer buffer) {
        return new ReadOnlyUnsafeDirectByteBuf(UnpooledByteBufAllocator.DEFAULT, buffer);
    }

    @Test
    @Override
    public void testMemoryAddress() {
        ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer());
        try {
            assertTrue(buf.hasMemoryAddress());
            buf.memoryAddress();
        } finally {
            buf.release();
        }
    }

    @Test
    public void testGettersRejectOutOfBoundsIndex() {
        final ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer());
        try {
            assertOutOfBounds(new Executable() {
                @Override
                public void execute() {
                    buf.getByte(8);
                }
            });
            assertOutOfBounds(new Executable() {
                @Override
                public void execute() {
                    buf.getByte(-1);
                }
            });
            assertOutOfBounds(new Executable() {
                @Override
                public void execute() {
                    buf.getShort(7);
                }
            });
            assertOutOfBounds(new Executable() {
                @Override
                public void execute() {
                    buf.getShortLE(7);
                }
            });
            assertOutOfBounds(new Executable() {
                @Override
                public void execute() {
                    buf.getUnsignedMedium(6);
                }
            });
            assertOutOfBounds(new Executable() {
                @Override
                public void execute() {
                    buf.getUnsignedMediumLE(6);
                }
            });
            assertOutOfBounds(new Executable() {
                @Override
                public void execute() {
                    buf.getInt(5);
                }
            });
            assertOutOfBounds(new Executable() {
                @Override
                public void execute() {
                    buf.getIntLE(5);
                }
            });
            assertOutOfBounds(new Executable() {
                @Override
                public void execute() {
                    buf.getLong(1);
                }
            });
            assertOutOfBounds(new Executable() {
                @Override
                public void execute() {
                    buf.getLongLE(1);
                }
            });
            assertOutOfBounds(new Executable() {
                @Override
                public void execute() {
                    buf.getLong(-1);
                }
            });
            assertOutOfBounds(new Executable() {
                @Override
                public void execute() {
                    buf.getLong(Integer.MAX_VALUE - 7);
                }
            });
            assertOutOfBounds(new Executable() {
                @Override
                public void execute() {
                    buf.getLong(Integer.MIN_VALUE);
                }
            });
        } finally {
            buf.release();
        }
    }

    private static void assertOutOfBounds(Executable executable) {
        assertThrows(IndexOutOfBoundsException.class, executable);
    }
}
