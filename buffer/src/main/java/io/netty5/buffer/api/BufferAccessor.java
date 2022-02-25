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
package io.netty5.buffer.api;

/**
 * This interface is just the primitive data accessor methods that {@link Buffer} exposes.
 * It can be useful if you only need the data access methods, and perhaps wish to decorate or modify their behaviour.
 * Usually, you'd use the {@link Buffer} interface directly, since this lets you properly control the buffer reference
 * count.
 */
public interface BufferAccessor {
    // <editor-fold defaultstate="collapsed" desc="Primitive accessors interface.">
    /**
     * Read the byte value at the current {@link Buffer#readerOffset()},
     * and increases the reader offset by {@link Byte#BYTES}.
     * The value is read using a two's complement 8-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @return The byte value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buffer#readableBytes} is less than {@link Byte#BYTES}.
     */
    byte readByte();

    /**
     * Get the byte value at the given reader offset.
     * The {@link Buffer#readerOffset()} is not modified.
     * The value is read using a two's complement 8-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param roff The read offset, an absolute offset into this buffer, to read from.
     * @return The byte value at the given offset.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus {@link Byte#BYTES}.
     */
    byte getByte(int roff);

    /**
     * Read the unsigned byte value at the current {@link Buffer#readerOffset()},
     * and increases the reader offset by {@link Byte#BYTES}.
     * The value is read using an unsigned two's complement 8-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @return The unsigned byte value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buffer#readableBytes} is less than {@link Byte#BYTES}.
     */
    int readUnsignedByte();

    /**
     * Get the unsigned byte value at the given reader offset.
     * The {@link Buffer#readerOffset()} is not modified.
     * The value is read using an unsigned two's complement 8-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param roff The read offset, an absolute offset into this buffer, to read from.
     * @return The unsigned byte value at the given offset.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus {@link Byte#BYTES}.
     */
    int getUnsignedByte(int roff);

    /**
     * Write the given byte value at the current {@link Buffer#writerOffset()},
     * and increase the writer offset by {@link Byte#BYTES}.
     * The value is written using a two's complement 8-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param value The byte value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException If {@link Buffer#writableBytes} is less than {@link Byte#BYTES}.
     */
    Buffer writeByte(byte value);

    /**
     * Set the given byte value at the given write offset. The {@link Buffer#writerOffset()} is not modified.
     * The value is written using a two's complement 8-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param woff The write offset, an absolute offset into this buffer to write to.
     * @param value The byte value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus {@link Byte#BYTES}.
     */
    Buffer setByte(int woff, byte value);

    /**
     * Write the given unsigned byte value at the current {@link Buffer#writerOffset()},
     * and increase the writer offset by {@link Byte#BYTES}.
     * The value is written using an unsigned two's complement 8-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param value The int value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException If {@link Buffer#writableBytes} is less than {@link Byte#BYTES}.
     */
    Buffer writeUnsignedByte(int value);

    /**
     * Set the given unsigned byte value at the given write offset. The {@link Buffer#writerOffset()} is not modified.
     * The value is written using an unsigned two's complement 8-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param woff The write offset, an absolute offset into this buffer to write to.
     * @param value The int value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus {@link Byte#BYTES}.
     */
    Buffer setUnsignedByte(int woff, int value);

    /**
     * Read the char value at the current {@link Buffer#readerOffset()},
     * and increases the reader offset by 2.
     * The value is read using a 2-byte UTF-16 encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @return The char value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buffer#readableBytes} is less than 2.
     */
    char readChar();

    /**
     * Get the char value at the given reader offset.
     * The {@link Buffer#readerOffset()} is not modified.
     * The value is read using a 2-byte UTF-16 encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param roff The read offset, an absolute offset into this buffer, to read from.
     * @return The char value at the given offset.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus 2.
     */
    char getChar(int roff);

    /**
     * Write the given char value at the current {@link Buffer#writerOffset()},
     * and increase the writer offset by 2.
     * The value is written using a 2-byte UTF-16 encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param value The char value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException If {@link Buffer#writableBytes} is less than 2.
     */
    Buffer writeChar(char value);

    /**
     * Set the given char value at the given write offset. The {@link Buffer#writerOffset()} is not modified.
     * The value is written using a 2-byte UTF-16 encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param woff The write offset, an absolute offset into this buffer to write to.
     * @param value The char value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus 2.
     */
    Buffer setChar(int woff, char value);

    /**
     * Read the short value at the current {@link Buffer#readerOffset()},
     * and increases the reader offset by {@link Short#BYTES}.
     * The value is read using a two's complement 16-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @return The short value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buffer#readableBytes} is less than {@link Short#BYTES}.
     */
    short readShort();

    /**
     * Get the short value at the given reader offset.
     * The {@link Buffer#readerOffset()} is not modified.
     * The value is read using a two's complement 16-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param roff The read offset, an absolute offset into this buffer, to read from.
     * @return The short value at the given offset.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus {@link Short#BYTES}.
     */
    short getShort(int roff);

    /**
     * Read the unsigned short value at the current {@link Buffer#readerOffset()},
     * and increases the reader offset by {@link Short#BYTES}.
     * The value is read using an unsigned two's complement 16-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @return The unsigned short value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buffer#readableBytes} is less than {@link Short#BYTES}.
     */
    int readUnsignedShort();

    /**
     * Get the unsigned short value at the given reader offset.
     * The {@link Buffer#readerOffset()} is not modified.
     * The value is read using an unsigned two's complement 16-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param roff The read offset, an absolute offset into this buffer, to read from.
     * @return The unsigned short value at the given offset.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus {@link Short#BYTES}.
     */
    int getUnsignedShort(int roff);

    /**
     * Write the given short value at the current {@link Buffer#writerOffset()},
     * and increase the writer offset by {@link Short#BYTES}.
     * The value is written using a two's complement 16-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param value The short value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException If {@link Buffer#writableBytes} is less than {@link Short#BYTES}.
     */
    Buffer writeShort(short value);

    /**
     * Set the given short value at the given write offset. The {@link Buffer#writerOffset()} is not modified.
     * The value is written using a two's complement 16-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param woff The write offset, an absolute offset into this buffer to write to.
     * @param value The short value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus {@link Short#BYTES}.
     */
    Buffer setShort(int woff, short value);

    /**
     * Write the given unsigned short value at the current {@link Buffer#writerOffset()},
     * and increase the writer offset by {@link Short#BYTES}.
     * The value is written using an unsigned two's complement 16-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param value The int value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException If {@link Buffer#writableBytes} is less than {@link Short#BYTES}.
     */
    Buffer writeUnsignedShort(int value);

    /**
     * Set the given unsigned short value at the given write offset. The {@link Buffer#writerOffset()} is not modified.
     * The value is written using an unsigned two's complement 16-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param woff The write offset, an absolute offset into this buffer to write to.
     * @param value The int value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus {@link Short#BYTES}.
     */
    Buffer setUnsignedShort(int woff, int value);

    /**
     * Read the int value at the current {@link Buffer#readerOffset()},
     * and increases the reader offset by 3.
     * The value is read using a two's complement 24-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @return The int value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buffer#readableBytes} is less than 3.
     */
    int readMedium();

    /**
     * Get the int value at the given reader offset.
     * The {@link Buffer#readerOffset()} is not modified.
     * The value is read using a two's complement 24-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param roff The read offset, an absolute offset into this buffer, to read from.
     * @return The int value at the given offset.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus 3.
     */
    int getMedium(int roff);

    /**
     * Read the unsigned int value at the current {@link Buffer#readerOffset()},
     * and increases the reader offset by 3.
     * The value is read using an unsigned two's complement 24-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @return The unsigned int value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buffer#readableBytes} is less than 3.
     */
    int readUnsignedMedium();

    /**
     * Get the unsigned int value at the given reader offset.
     * The {@link Buffer#readerOffset()} is not modified.
     * The value is read using an unsigned two's complement 24-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param roff The read offset, an absolute offset into this buffer, to read from.
     * @return The unsigned int value at the given offset.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus 3.
     */
    int getUnsignedMedium(int roff);

    /**
     * Write the given int value at the current {@link Buffer#writerOffset()},
     * and increase the writer offset by 3.
     * The value is written using a two's complement 24-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param value The int value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException If {@link Buffer#writableBytes} is less than 3.
     */
    Buffer writeMedium(int value);

    /**
     * Set the given int value at the given write offset. The {@link Buffer#writerOffset()} is not modified.
     * The value is written using a two's complement 24-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param woff The write offset, an absolute offset into this buffer to write to.
     * @param value The int value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus 3.
     */
    Buffer setMedium(int woff, int value);

    /**
     * Write the given unsigned int value at the current {@link Buffer#writerOffset()},
     * and increase the writer offset by 3.
     * The value is written using an unsigned two's complement 24-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param value The int value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException If {@link Buffer#writableBytes} is less than 3.
     */
    Buffer writeUnsignedMedium(int value);

    /**
     * Set the given unsigned int value at the given write offset. The {@link Buffer#writerOffset()} is not modified.
     * The value is written using an unsigned two's complement 24-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param woff The write offset, an absolute offset into this buffer to write to.
     * @param value The int value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus 3.
     */
    Buffer setUnsignedMedium(int woff, int value);

    /**
     * Read the int value at the current {@link Buffer#readerOffset()},
     * and increases the reader offset by {@link Integer#BYTES}.
     * The value is read using a two's complement 32-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @return The int value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buffer#readableBytes} is less than {@link Integer#BYTES}.
     */
    int readInt();

    /**
     * Get the int value at the given reader offset.
     * The {@link Buffer#readerOffset()} is not modified.
     * The value is read using a two's complement 32-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param roff The read offset, an absolute offset into this buffer, to read from.
     * @return The int value at the given offset.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus {@link Integer#BYTES}.
     */
    int getInt(int roff);

    /**
     * Read the unsigned int value at the current {@link Buffer#readerOffset()},
     * and increases the reader offset by {@link Integer#BYTES}.
     * The value is read using an unsigned two's complement 32-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @return The unsigned int value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buffer#readableBytes} is less than {@link Integer#BYTES}.
     */
    long readUnsignedInt();

    /**
     * Get the unsigned int value at the given reader offset.
     * The {@link Buffer#readerOffset()} is not modified.
     * The value is read using an unsigned two's complement 32-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param roff The read offset, an absolute offset into this buffer, to read from.
     * @return The unsigned int value at the given offset.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus {@link Integer#BYTES}.
     */
    long getUnsignedInt(int roff);

    /**
     * Write the given int value at the current {@link Buffer#writerOffset()},
     * and increase the writer offset by {@link Integer#BYTES}.
     * The value is written using a two's complement 32-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param value The int value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException If {@link Buffer#writableBytes} is less than {@link Integer#BYTES}.
     */
    Buffer writeInt(int value);

    /**
     * Set the given int value at the given write offset. The {@link Buffer#writerOffset()} is not modified.
     * The value is written using a two's complement 32-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param woff The write offset, an absolute offset into this buffer to write to.
     * @param value The int value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus {@link Integer#BYTES}.
     */
    Buffer setInt(int woff, int value);

    /**
     * Write the given unsigned int value at the current {@link Buffer#writerOffset()},
     * and increase the writer offset by {@link Integer#BYTES}.
     * The value is written using an unsigned two's complement 32-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param value The long value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException If {@link Buffer#writableBytes} is less than {@link Integer#BYTES}.
     */
    Buffer writeUnsignedInt(long value);

    /**
     * Set the given unsigned int value at the given write offset. The {@link Buffer#writerOffset()} is not modified.
     * The value is written using an unsigned two's complement 32-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param woff The write offset, an absolute offset into this buffer to write to.
     * @param value The long value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus {@link Integer#BYTES}.
     */
    Buffer setUnsignedInt(int woff, long value);

    /**
     * Read the float value at the current {@link Buffer#readerOffset()},
     * and increases the reader offset by {@link Float#BYTES}.
     * The value is read using a 32-bit IEEE floating point encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @return The float value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buffer#readableBytes} is less than {@link Float#BYTES}.
     */
    float readFloat();

    /**
     * Get the float value at the given reader offset.
     * The {@link Buffer#readerOffset()} is not modified.
     * The value is read using a 32-bit IEEE floating point encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param roff The read offset, an absolute offset into this buffer, to read from.
     * @return The float value at the given offset.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus {@link Float#BYTES}.
     */
    float getFloat(int roff);

    /**
     * Write the given float value at the current {@link Buffer#writerOffset()},
     * and increase the writer offset by {@link Float#BYTES}.
     * The value is written using a 32-bit IEEE floating point encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param value The float value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException If {@link Buffer#writableBytes} is less than {@link Float#BYTES}.
     */
    Buffer writeFloat(float value);

    /**
     * Set the given float value at the given write offset. The {@link Buffer#writerOffset()} is not modified.
     * The value is written using a 32-bit IEEE floating point encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param woff The write offset, an absolute offset into this buffer to write to.
     * @param value The float value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus {@link Float#BYTES}.
     */
    Buffer setFloat(int woff, float value);

    /**
     * Read the long value at the current {@link Buffer#readerOffset()},
     * and increases the reader offset by {@link Long#BYTES}.
     * The value is read using a two's complement 64-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @return The long value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buffer#readableBytes} is less than {@link Long#BYTES}.
     */
    long readLong();

    /**
     * Get the long value at the given reader offset.
     * The {@link Buffer#readerOffset()} is not modified.
     * The value is read using a two's complement 64-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param roff The read offset, an absolute offset into this buffer, to read from.
     * @return The long value at the given offset.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus {@link Long#BYTES}.
     */
    long getLong(int roff);

    /**
     * Write the given long value at the current {@link Buffer#writerOffset()},
     * and increase the writer offset by {@link Long#BYTES}.
     * The value is written using a two's complement 64-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param value The long value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException If {@link Buffer#writableBytes} is less than {@link Long#BYTES}.
     */
    Buffer writeLong(long value);

    /**
     * Set the given long value at the given write offset. The {@link Buffer#writerOffset()} is not modified.
     * The value is written using a two's complement 64-bit encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param woff The write offset, an absolute offset into this buffer to write to.
     * @param value The long value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus {@link Long#BYTES}.
     */
    Buffer setLong(int woff, long value);

    /**
     * Read the double value at the current {@link Buffer#readerOffset()},
     * and increases the reader offset by {@link Double#BYTES}.
     * The value is read using a 64-bit IEEE floating point encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @return The double value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buffer#readableBytes} is less than {@link Double#BYTES}.
     */
    double readDouble();

    /**
     * Get the double value at the given reader offset.
     * The {@link Buffer#readerOffset()} is not modified.
     * The value is read using a 64-bit IEEE floating point encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param roff The read offset, an absolute offset into this buffer, to read from.
     * @return The double value at the given offset.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus {@link Double#BYTES}.
     */
    double getDouble(int roff);

    /**
     * Write the given double value at the current {@link Buffer#writerOffset()},
     * and increase the writer offset by {@link Double#BYTES}.
     * The value is written using a 64-bit IEEE floating point encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param value The double value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException If {@link Buffer#writableBytes} is less than {@link Double#BYTES}.
     */
    Buffer writeDouble(double value);

    /**
     * Set the given double value at the given write offset. The {@link Buffer#writerOffset()} is not modified.
     * The value is written using a 64-bit IEEE floating point encoding,
     * in {@link java.nio.ByteOrder#BIG_ENDIAN} byte order.
     *
     * @param woff The write offset, an absolute offset into this buffer to write to.
     * @param value The double value to write.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than {@link Buffer#capacity()} minus {@link Double#BYTES}.
     */
    Buffer setDouble(int woff, double value);
    // </editor-fold>
}
