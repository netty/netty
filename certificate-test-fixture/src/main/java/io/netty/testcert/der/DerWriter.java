/*
 * Copyright 2024 The Netty Project
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
package io.netty.testcert.der;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.util.internal.UnstableApi;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoLocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MILLI_OF_SECOND;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;

/**
 * An encoder of ASN.1 DER, ITU-T X.690 (ISO/IEC 8825-1:2021) Section 10,
 * supporting just enough features to implement ITU-T X.509 (ISO/IEC 9594-8:2020).
 */
@UnstableApi
public final class DerWriter implements ByteBufHolder, AutoCloseable {
    private static final int TAG_BOOLEAN = 0x01;
    private static final int TAG_INTEGER = 0x02;
    private static final int TAG_BIT_STRING = 0x03;
    private static final int TAG_OCTET_STRING = 0x04;
    private static final int TAG_OBJECT_IDENTIFIER = 0x06;
    private static final int TAG_ENUMERATED = 0x0A;
    private static final int TAG_UTF8_STRING = 0x0C;
    private static final int TAG_SEQUENCE = 0x30; // Constructed 0x10
    private static final int TAG_SET = 0x31; // Constructed 0x11
    private static final int TAG_IA5STRING = 0x16;
    private static final int TAG_UTC_TIME = 0x17;
    private static final int TAG_GENERALIZED_TIME = 0x18;
    private static final MethodHandle LONG_EXPAND;
    private static final ZoneId ZONE_UTC = ZoneId.of("Z");

    public static final int TAG_CONSTRUCTED = 0x20;
    public static final int TAG_CONTEXT = 0x80;

    static {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        MethodType type = MethodType.methodType(long.class, long.class, long.class);
        MethodHandle handle;
        try {
            // Long.expand(long, long) was added in Java 19.
            handle = lookup.findStatic(Long.class, "expand", type);
        } catch (Throwable e) {
            handle = null;
        }
        LONG_EXPAND = handle;
    }

    private final ByteBuf buf;
    private final Runnable onWrite;

    public DerWriter() {
        this(null);
    }

    DerWriter(Runnable onWrite) {
        this(Unpooled.buffer(), onWrite);
    }

    DerWriter(ByteBuf buf, Runnable onWrite) {
        this.buf = Objects.requireNonNull(buf, "buf");
        this.onWrite = onWrite;
    }

    public byte[] getBytes() {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), bytes);
        return bytes;
    }

    public void writeBytesInto(ByteBuf dst) {
        buf.getBytes(buf.readerIndex(), dst);
    }

    public void reset() {
        buf.setIndex(0, 0);
    }

    @Override
    public int refCnt() {
        return buf.refCnt();
    }

    @Override
    public ByteBuf content() {
        return buf;
    }

    @Override
    public DerWriter copy() {
        return new DerWriter(buf.copy(), null);
    }

    @Override
    public DerWriter duplicate() {
        return new DerWriter(buf.duplicate(), null);
    }

    @Override
    public DerWriter retainedDuplicate() {
        return new DerWriter(buf.retainedDuplicate(), null);
    }

    @Override
    public DerWriter replace(ByteBuf content) {
        return new DerWriter(content, null);
    }

    @Override
    public DerWriter retain() {
        buf.retain();
        return this;
    }

    @Override
    public DerWriter retain(int increment) {
        buf.retain(increment);
        return this;
    }

    @Override
    public DerWriter touch() {
        buf.touch();
        return this;
    }

    @Override
    public DerWriter touch(Object hint) {
        buf.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return buf.release();
    }

    @Override
    public boolean release(int decrement) {
        return buf.release(decrement);
    }

    @Override
    public void close() {
        release();
    }

    private void writeTagLen(int tag, int len) {
        if (tag > 255) {
            throw new UnsupportedOperationException("DerWriter does not support encoding multi-byte tags.");
        }
        if (len < 0) {
            throw new IllegalArgumentException("Length cannot be negative: " + len);
        }
        if (onWrite != null) {
            onWrite.run();
        }
        buf.writeByte(tag);
        writeLen(len);
    }

    private void writeLen(int len) {
        if (len <= Byte.MAX_VALUE) {
            // Short form
            buf.writeByte(len);
        } else if (len < 1 << 8) {
            // Long form 1 byte
            buf.writeByte(0x81);
            buf.writeByte(len);
        } else if (len < 1 << 16) {
            // Long form 2 bytes
            buf.writeByte(0x82);
            buf.writeShort(len);
        } else if (len < 1 << 24) {
            // Long form 3 bytes
            buf.writeByte(0x83);
            buf.writeMedium(len);
        } else {
            // Long form 4 bytes
            buf.writeByte(0x84);
            buf.writeInt(len);
        }
    }

    public DerWriter writeRawDER(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (onWrite != null) {
            onWrite.run();
        }
        buf.writeBytes(bytes);
        return this;
    }

    public DerWriter writeBoolean(boolean value) {
        writeTagLen(TAG_BOOLEAN, 1);
        buf.writeByte(value ? 0xFF : 0x00);
        return this;
    }

    public DerWriter writeInteger(BigInteger value) {
        return writeInteger(TAG_INTEGER, value);
    }

    public DerWriter writeInteger(int tag, BigInteger value) {
        byte[] bytes = value.toByteArray();
        writeTagLen(tag, bytes.length);
        buf.writeBytes(bytes);
        return this;
    }

    public DerWriter writeInteger(long value) {
        return writeInteger(TAG_INTEGER, value);
    }

    public DerWriter writeInteger(int tag, long value) {
        writeTagLen(tag, 1);
        if (value == 0) {
            buf.writeByte(0);
        } else if (value == -1) {
            buf.writeByte(0xFF);
        } else {
            int tagPos = buf.writerIndex() - 1;
            boolean initial = true;
            int bytesLeft = Long.BYTES;
            if (value > 0) {
                // Positive
                while (--bytesLeft >= 0) {
                    int v = (int) (value >> bytesLeft * Byte.SIZE & 0xFF);
                    if (v == 0 && initial) {
                        continue;
                    }
                    if (initial && (v & 0x80) == 0x80) {
                        buf.writeByte(0x00);
                    }
                    initial = false;
                    buf.writeByte(v);
                }
            } else {
                // Negative
                while (--bytesLeft >= 0) {
                    int v = (int) (value >> bytesLeft * Byte.SIZE & 0xFF);
                    if (v == 0xFF && initial) {
                        continue;
                    }
                    if (initial && (v & 0x80) != 0x80) {
                        buf.writeByte(0xFF);
                    }
                    initial = false;
                    buf.writeByte(v);
                }
            }
            buf.setByte(tagPos, buf.writerIndex() - tagPos - 1); // Update the length
        }
        return this;
    }

    public DerWriter writeEnumerated(long value) {
        return writeInteger(TAG_ENUMERATED, value);
    }

    public DerWriter writeOctetString(byte[] bytes) {
        return writeOctetString(TAG_OCTET_STRING, bytes);
    }

    public DerWriter writeOctetString(int tag, byte[] bytes) {
        writeTagLen(tag, bytes.length);
        buf.writeBytes(bytes);
        return this;
    }

    public DerWriter writeOctetString(ByteBuf bytes) {
        return writeOctetString(TAG_OCTET_STRING, bytes);
    }

    public DerWriter writeOctetString(int tag, ByteBuf bytes) {
        writeTagLen(tag, bytes.readableBytes());
        buf.writeBytes(bytes);
        return this;
    }

    public DerWriter writeIA5String(String string) {
        return writeIA5String(TAG_IA5STRING, string);
    }

    public DerWriter writeIA5String(int tag, String string) {
        byte[] ascii = string.getBytes(StandardCharsets.US_ASCII);
        writeTagLen(tag, ascii.length);
        buf.writeBytes(ascii);
        return this;
    }

    public DerWriter writeUTF8String(String string) {
        return writeUTF8String(TAG_UTF8_STRING, string);
    }

    public DerWriter writeUTF8String(int tag, String string) {
        byte[] utf8 = string.getBytes(StandardCharsets.UTF_8);
        writeTagLen(tag, utf8.length);
        buf.writeBytes(utf8);
        return this;
    }

    public DerWriter writeBitString(boolean[] bits) {
        return writeBitString(TAG_BIT_STRING, bits);
    }

    public DerWriter writeBitString(int tag, boolean[] bits) {
        int padding = 8 - bits.length % 8;
        int lenBytes = bits.length / 8 + 1;
        if (padding == 8) {
            padding = 0;
            lenBytes--;
        }
        byte[] bytes = new byte[lenBytes];
        for (int i = 0; i < bits.length; i++) {
            if (bits[i]) {
                int byteIndex = i / 8;
                int bitIndex = i % 8;
                bytes[byteIndex] |= (byte) (0x80 >>> bitIndex);
            }
        }
        return writeBitString(tag, bytes, padding);
    }

    public DerWriter writeBitString(byte[] bytes, int lastBytePaddingBits) {
        return writeBitString(TAG_BIT_STRING, bytes, lastBytePaddingBits);
    }

    public DerWriter writeBitString(int tag, byte[] bytes, int lastBytePaddingBits) {
        if (bytes.length == 0 && lastBytePaddingBits != 0) {
            throw new IllegalArgumentException("Empty BIT STRINGs cannot have padding bits.");
        }
        if (lastBytePaddingBits > 7) {
            throw new IllegalArgumentException("A BIT STRING cannot have more than 7 padding bits.");
        }
        if (lastBytePaddingBits < 0) {
            throw new IllegalArgumentException("Illegal padding bits value: " + lastBytePaddingBits);
        }
        byte lastByteMask = (byte) (0xFF << lastBytePaddingBits & 0xFF);
        bytes[bytes.length - 1] &= lastByteMask;
        writeTagLen(tag, 1 + bytes.length);
        buf.writeByte(lastBytePaddingBits);
        buf.writeBytes(bytes);
        return this;
    }

    public DerWriter writeObjectIdentifier(String oid) {
        return writeObjectIdentifier(TAG_OBJECT_IDENTIFIER, oid);
    }

    public DerWriter writeObjectIdentifier(int tag, String oid) {
        String[] strParts = oid.split("\\.");
        long[] longParts = new long[strParts.length];
        for (int i = 0; i < longParts.length; i++) {
            String str = strParts[i];
            if (str.length() == 0 || str.charAt(0) == '+') {
                // Long.parseUnsignedLong() permits '+' as first character, but we don't, so we have to reject it here.
                throw new NumberFormatException(str);
            }
            longParts[i] = Long.parseUnsignedLong(str);
        }
        return writeObjectIdentifier(tag, longParts);
    }

    public DerWriter writeObjectIdentifier(long[] oid) {
        return writeObjectIdentifier(TAG_OBJECT_IDENTIFIER, oid);
    }

    public DerWriter writeObjectIdentifier(int tag, long[] oid) {
        if (oid.length < 2) {
            throw new IllegalArgumentException("An OBJECT IDENTIFIER must have at least two sub-identifiers.");
        }
        long root = oid[0];
        long child = oid[1];
        if (root != 0 && root != 1 && root != 2) {
            throw new IllegalArgumentException("Invalid root node: " + root);
        }
        if ((root == 0 || root == 1) && child > 39) {
            throw new IllegalArgumentException("Root nodes 0 and 1 cannot have child nodes greater than 39.");
        }
        for (int i = 0; i < oid.length; i++) {
            long identifier = oid[i];
            int adjust = 0;
            if (i == 1) {
                adjust = root == 0 ? 0 : root == 1 ? 40 : 80;
            }
            if (Long.compareUnsigned(identifier, 0x00FFFFFF_FFFFFFFFL - adjust) > 0 || identifier < -adjust) {
                throw new UnsupportedOperationException("Sub-identifier too big to encode: " + identifier);
            }
        }

        writeTagLen(tag, 0); // Optimistically assume one length byte is enough.
        int lenIndex = buf.writerIndex();

        if (root == 0) {
            writeOidSubidentifier(child);
        } else if (root == 1) {
            writeOidSubidentifier(child + 40);
        } else {
            writeOidSubidentifier(child + 80);
        }

        for (int i = 2; i < oid.length; i++) {
            writeOidSubidentifier(oid[i]);
        }

        int actualLength = buf.writerIndex() - lenIndex;
        if (actualLength > 127) {
            // Move the data to make room for the necessary length bytes.
            byte[] data = new byte[actualLength];
            buf.getBytes(lenIndex, data);
            buf.writerIndex(lenIndex - 1);
            writeLen(actualLength);
            buf.writeBytes(data);
        } else {
            // One length byte was enough. Update the header with the actual length.
            buf.setByte(lenIndex - 1, actualLength);
        }
        return this;
    }

    private void writeOidSubidentifier(long identifier) {
        long unmarkedBytes = expand(identifier, 0x7F7F7F7F_7F7F7F7FL);
        int lengthInBytes = Long.BYTES - Long.numberOfLeadingZeros(unmarkedBytes) / 8;
        long value = unmarkedBytes | 0x80808080_80808000L;
        switch (lengthInBytes) {
            case 8: buf.writeLong(value); break;
            case 7: buf.writeByte((int) (value >>> 56 & 0xFF));
            case 6: buf.writeByte((int) (value >>> 48 & 0xFF));
            case 5: buf.writeByte((int) (value >>> 40 & 0xFF));
            case 4: buf.writeInt((int) value); break;
            case 3: buf.writeMedium((int) value); break;
            case 2: buf.writeShort((int) value); break;
            default: buf.writeByte((int) value); break;
        }
    }

    private static long expand(long value, long mask) {
        if (LONG_EXPAND != null) {
            try {
                return (long) LONG_EXPAND.invokeExact(value, mask);
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
        }
        return expandSlow(value, mask);
    }

    private static long expandSlow(long value, long mask) {
        long result = 0;
        long valueBit = 1;
        long maskBit = 1;
        int outShift = 0;
        do {
            if ((mask & maskBit) != 0) {
                result += value & valueBit << outShift;
                valueBit <<= 1;
            } else {
                outShift++;
            }
            maskBit <<= 1;
        } while (maskBit != 0);
        return result;
    }

    public DerWriter writeSequence(WritableSequence sequenceWriter) {
        return writeSequence(TAG_SEQUENCE, sequenceWriter);
    }

    public DerWriter writeSequence(int tag, WritableSequence sequenceWriter) {
        try (DerWriter sequence = new DerWriter()) {
            sequenceWriter.writeSequence(sequence);
            ByteBuf innerBuf = sequence.buf;
            writeTagLen(tag, innerBuf.readableBytes());
            buf.writeBytes(innerBuf);
        }
        return this;
    }

    private static final DateTimeFormatter UTC_TIME = new DateTimeFormatterBuilder()
            .appendValueReduced(YEAR, 2, 2, ChronoLocalDate.from(LocalDate.of(1900, 1, 1)))
            .appendValue(MONTH_OF_YEAR, 2)
            .appendValue(DAY_OF_MONTH, 2)
            .appendValue(HOUR_OF_DAY, 2)
            .appendValue(MINUTE_OF_HOUR, 2)
            .appendValue(SECOND_OF_MINUTE, 2)
            .appendOffset("+HHMM", "Z")
            .toFormatter(Locale.ROOT);

    public DerWriter writeUTCTime(Instant time) {
        return writeUTCTime(ZonedDateTime.ofInstant(time, ZONE_UTC));
    }

    public DerWriter writeUTCTime(ZonedDateTime time) {
        String timeStr = UTC_TIME.format(time);
        byte[] bytes = timeStr.getBytes(StandardCharsets.US_ASCII);
        writeTagLen(TAG_UTC_TIME, bytes.length);
        buf.writeBytes(bytes);
        return this;
    }

    public DerWriter writeGeneralizedTime(ZonedDateTime time) {
        return innerWriteGeneralizedTime(TAG_GENERALIZED_TIME, time);
    }

    public DerWriter writeGeneralizedTime(int tag, ZonedDateTime time) {
        return innerWriteGeneralizedTime(tag, time);
    }

    public DerWriter writeGeneralizedTime(LocalDateTime time) {
        return innerWriteGeneralizedTime(TAG_GENERALIZED_TIME, time);
    }

    public DerWriter writeGeneralizedTime(int tag, LocalDateTime time) {
        return innerWriteGeneralizedTime(tag, time);
    }

    public DerWriter writeGeneralizedTime(LocalDate time) {
        return innerWriteGeneralizedTime(TAG_GENERALIZED_TIME, time);
    }

    public DerWriter writeGeneralizedTime(int tag, LocalDate time) {
        return innerWriteGeneralizedTime(tag, time);
    }

    public DerWriter writeGeneralizedTime(Instant time) {
        return innerWriteGeneralizedTime(TAG_GENERALIZED_TIME, ZonedDateTime.ofInstant(time, ZONE_UTC));
    }

    public DerWriter writeGeneralizedTime(int tag, Instant time) {
        return innerWriteGeneralizedTime(tag, ZonedDateTime.ofInstant(time, ZONE_UTC));
    }

    private static final DateTimeFormatter GENERALIZED_TIME = new DateTimeFormatterBuilder()
            .appendValue(YEAR, 4, 4, SignStyle.NOT_NEGATIVE)
            .appendValue(MONTH_OF_YEAR, 2)
            .appendValue(DAY_OF_MONTH, 2)
            .optionalStart()
            .appendValue(HOUR_OF_DAY, 2)
            .appendValue(MINUTE_OF_HOUR, 2)
            .appendValue(SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendLiteral('.')
            .appendValue(MILLI_OF_SECOND)
            .optionalEnd()
            .optionalStart()
            .appendOffset("+HHMM", "Z")
            .toFormatter(Locale.ROOT);

    private DerWriter innerWriteGeneralizedTime(int tag, TemporalAccessor time) {
        String timeStr = GENERALIZED_TIME.format(time);
        byte[] bytes = timeStr.getBytes(StandardCharsets.US_ASCII);
        writeTagLen(tag, bytes.length);
        buf.writeBytes(bytes);
        return this;
    }

    public DerWriter writeExplicit(int tag, Consumer<DerWriter> explicitWriter) {
        if ((tag & 0x20) != 0x20) {
            throw new IllegalArgumentException("Tag must be CONSTRUCTED, i.e. have bit 0b00100000 raised.");
        }
        DerWriter writer = new DerWriter(new WriteOnlyOnce());
        try {
            explicitWriter.accept(writer);
            ByteBuf innerBuf = writer.buf;
            writeTagLen(tag, innerBuf.readableBytes());
            buf.writeBytes(innerBuf);
        } finally {
            writer.release();
        }
        return this;
    }

    private static final class WriteOnlyOnce extends AtomicBoolean implements Runnable {
        private static final long serialVersionUID = -1583627828906639617L;

        @Override
        public void run() {
            if (getAndSet(true)) {
                throw new IllegalStateException("Only one element can be written.");
            }
        }
    }

    @FunctionalInterface
    public interface WritableSequence {
        void writeSequence(DerWriter writer);
    }
}
