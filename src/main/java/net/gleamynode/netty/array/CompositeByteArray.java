/*
 * Copyright (C) 2008  Trustin Heuiimport java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
tion) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.array;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;


public class CompositeByteArray extends AbstractByteArrayBuffer implements Iterable<ByteArray> {

    static final int DEFAULT_CAPACITY_INCREMENT = 512;

    private final int capacityIncrement;

    Component head;
    private Component tail;

    /**
     * Refers to {@link #tail} if the current tail can be used as a room for
     * primitive write operations.  {@code null} otherwise.
     */
    private Component tailRoom;

    // Cache recently accessed component for performance boost
    private Component recentlyAccessed;
    private int count;
    int length;
    int modCount;

    public CompositeByteArray() {
        this(DEFAULT_CAPACITY_INCREMENT);
    }

    public CompositeByteArray(int capacityIncrement) {
        this(capacityIncrement, ByteArray.EMPTY_BUFFER);
    }

    public CompositeByteArray(ByteArray array) {
        this(DEFAULT_CAPACITY_INCREMENT, array);
    }

    public CompositeByteArray(int capacityIncrement, ByteArray array) {
        if (capacityIncrement < 8) {
            throw new IllegalArgumentException(
                    "capacityIncrement should be equal to or greater than 8.");
        }
        if (array == null) {
            throw new NullPointerException("array");
        }

        this.capacityIncrement = capacityIncrement;
        init(array, 0);
    }

    public CompositeByteArray(ByteArray firstArray, ByteArray... otherArrays) {
        this(DEFAULT_CAPACITY_INCREMENT, firstArray, otherArrays);
    }

    public CompositeByteArray(int capacityIncrement, ByteArray firstArray, ByteArray... otherArrays) {
        if (capacityIncrement < 8) {
            throw new IllegalArgumentException(
                    "capacityIncrement should be equal to or greater than 8.");
        }
        if (firstArray == null) {
            throw new NullPointerException("firstArray");
        }
        if (otherArrays == null) {
            throw new NullPointerException("otherArrays");
        }

        this.capacityIncrement = capacityIncrement;
        init(firstArray, 0);
        for (int i = 0; i < otherArrays.length; i ++) {
            ByteArray a = otherArrays[i];
            if (a == null) {
                throw new NullPointerException("otherArrays[" + i + "]");
            }
            addLast(a);
        }
    }

    private void init(ByteArray array, int firstIndex) {
        length = 0;
        recentlyAccessed = head = tail = new Component(null, null, firstIndex, array);
        count = 1;
    }

    public void addFirst(ByteArray array) {
        if (array == null) {
            throw new NullPointerException("array");
        }

        if (array.empty()) {
            return;
        }

        if (array instanceof Iterable) {
            for (Object a: (Iterable<?>) array) {
                addFirst((ByteArray) a);
            }
            return;
        }

        if (empty()) {
            init(array, firstIndex());
            return;
        }

        Component oldHead = head;
        Component newHead = new Component(null, oldHead, oldHead.globalIndex() - array.length(), array);
        oldHead.prev(newHead);

        // Unlike removeFirst/removeLast, length is modified when a new
        // Component is created.
        //length += array.length();

        head = newHead;
        count ++;
        modCount ++;
    }

    public void addLast(ByteArray array) {
        if (array == null) {
            throw new NullPointerException("array");
        }

        if (array.empty()) {
            return;
        }

        if (array instanceof Iterable) {
            for (Object a: (Iterable<?>) array) {
                addLast((ByteArray) a);
            }
            return;
        }

        if (empty()) {
            init(array, firstIndex());
            return;
        }

        Component oldTail = tail;
        Component newTail = new Component(oldTail, null, oldTail.globalIndex() + oldTail.length(), array);
        oldTail.next(newTail);

        // Unlike removeFirst/removeLast, length is modified when a new
        // Component is created.
        //length += array.length();

        tail = newTail;
        tailRoom = null;
        count ++;
        modCount ++;
    }

    public ByteArray removeFirst() {
        ensurePositiveLength();
        if (head == tail) {
            ByteArray first = head;
            init(EMPTY_BUFFER, firstIndex() + first.length());
            return first;
        }

        Component oldHead = head;
        Component newHead = oldHead.next();
        newHead.prev(null);

        head = newHead;
        length -= oldHead.length();
        count --;
        modCount ++;
        return oldHead;
    }

    public ByteArray removeLast() {
        ensurePositiveLength();
        if (head == tail) {
            ByteArray last = tail;
            init(EMPTY_BUFFER, firstIndex());
            return last;
        }

        Component oldTail = tail;
        Component newTail = tail.prev();
        newTail.next(null);

        tail = newTail;
        tailRoom = null;
        length -= oldTail.length();
        count --;
        modCount ++;
        return oldTail;
    }

    public ByteArray first() {
        ensurePositiveCount();
        return head;
    }

    public ByteArray last() {
        ensurePositiveCount();
        return tail;
    }

    private void ensurePositiveLength() {
        if (empty()) {
            throw new NoSuchElementException();
        }
    }

    private void ensurePositiveCount() {
        if (count() == 0) {
            throw new NoSuchElementException();
        }
    }

    public int firstIndex() {
        return head.globalIndex();
    }

    public int length() {
        return length;
    }

    public int count() {
        return count;
    }

    public byte get8(int index) {
        Component c = component(index);
        return c.get8(index + c.adjustment());
    }

    public short getBE16(int index) {
        Component c1 = component(index);
        Component c2 = component(c1, index+1);
        if (c1 == c2) {
            return c1.getBE16(index + c1.adjustment());
        }

        return (short) ((c1.get8(index   + c1.adjustment()) & 0xff) << 8 |
                        (c2.get8(index+1 + c2.adjustment()) & 0xff) << 0);
    }

    public int getBE24(int index) {
        Component c1 = component(index);
        Component c3 = component(c1, index+2);
        if (c1 == c3) {
            return c1.getBE24(index + c1.adjustment());
        }

        return (getBE16(index) & 0xFFFF) << 8 | get8(index + 2) & 0xFF;
    }

    public int getBE32(int index) {
        Component c1 = component(index);
        Component c4 = component(c1, index+3);
        if (c1 == c4) {
            return c1.getBE32(index + c1.adjustment());
        }

        return (getBE16(index) & 0xFFFF) << 16 | getBE16(index + 2) & 0xFFFF;
    }

    public long getBE48(int index) {
        Component c1 = component(index);
        Component c6 = component(c1, index+5);
        if (c1 == c6) {
            return c1.getBE48(index + c1.adjustment());
        }

        return (getBE24(index) & 0xFFFFFFFFL) << 24 | getBE24(index + 3) & 0xFFFFFFFFL;
    }

    public long getBE64(int index) {
        Component c1 = component(index);
        Component c8 = component(c1, index+7);
        if (c1 == c8) {
            return c1.getBE64(index + c1.adjustment());
        }

        return (getBE32(index) & 0xFFFFFFFFL) << 32 | getBE32(index + 4) & 0xFFFFFFFFL;
    }

    public short getLE16(int index) {
        Component c1 = component(index);
        Component c2 = component(c1, index+1);
        if (c1 == c2) {
            return c1.getLE16(index + c1.adjustment());
        }

        return (short) ((c1.get8(index   + c1.adjustment()) & 0xff) << 0 |
                        (c2.get8(index+1 + c2.adjustment()) & 0xff) << 8);
    }

    public int getLE24(int index) {
        Component c1 = component(index);
        Component c3 = component(c1, index+2);
        if (c1 == c3) {
            return c1.getLE24(index + c1.adjustment());
        }

        return getLE16(index) & 0xFFFF | (get8(index + 2) & 0xFF) << 16;
    }

    public int getLE32(int index) {
        Component c1 = component(index);
        Component c4 = component(c1, index+3);
        if (c1 == c4) {
            return c1.getLE32(index + c1.adjustment());
        }

        return getLE16(index) & 0xFFFF | (getLE16(index + 2) & 0xFFFF) << 16;
    }

    public long getLE48(int index) {
        Component c1 = component(index);
        Component c6 = component(c1, index+5);
        if (c1 == c6) {
            return c1.getLE48(index + c1.adjustment());
        }

        return getLE24(index) & 0xFFFFFFFFL | (getLE24(index + 3) & 0xFFFFFFFFL) << 24;
    }

    public long getLE64(int index) {
        Component c1 = component(index);
        Component c8 = component(c1, index+7);
        if (c1 == c8) {
            return c1.getLE64(index + c1.adjustment());
        }

        return getLE32(index) & 0xFFFFFFFFL | (getLE32(index + 4) & 0xFFFFFFFFL) << 32;
    }

    public void get(int index, ByteArray dst, int dstIndex, int length) {
        int remaining = length;
        Component c = component(index);
        if (remaining == 0) {
            return;
        }

        for (;;) {
            int localSrcIndex = index + c.adjustment();
            int localLength = Math.min(remaining, c.globalEndIndex() - index);
            c.get(localSrcIndex, dst, dstIndex, localLength);

            index += localLength;
            dstIndex += localLength;
            remaining -= localLength;
            if (remaining == 0) {
                break;
            }
            c = c.next();
        }
    }

    public void get(int index, byte[] dst, int dstIndex, int length) {
        int remaining = length;
        Component c = component(index);
        if (remaining == 0) {
            return;
        }

        for (;;) {
            int localSrcIndex = index + c.adjustment();
            int localLength = Math.min(remaining, c.globalEndIndex() - index);
            c.get(localSrcIndex, dst, dstIndex, localLength);

            index += localLength;
            dstIndex += localLength;
            remaining -= localLength;
            if (remaining == 0) {
                break;
            }
            c = c.next();
        }
    }

    public void get(int index, ByteBuffer dst) {
        int limit = dst.limit();
        int remaining = dst.remaining();
        Component c = component(index);
        if (remaining == 0) {
            return;
        }

        try {
            for (;;) {
                int localSrcIndex = index + c.adjustment();
                int localLength = Math.min(remaining, c.globalEndIndex() - index);
                dst.limit(dst.position() + localLength);
                c.get(localSrcIndex, dst);

                index += localLength;
                remaining -= localLength;
                if (remaining == 0) {
                    break;
                }
                c = c.next();
            }
        } finally {
            dst.limit(limit);
        }
    }

    public void set8(int index, byte value) {
        Component c = component(index);
        c.set8(index + c.adjustment(), value);
    }

    public void setBE16(int index, short value) {
        Component c1 = component(index);
        Component c2 = component(c1, index+1);
        if (c1 == c2) {
            c1.setBE16(index + c1.adjustment(), value);
        } else {
            c1.set8(index   + c1.adjustment(), (byte) (value >>> 8));
            c2.set8(index+1 + c2.adjustment(), (byte) (value >>> 0));
        }
    }

    public void setBE24(int index, int value) {
        Component c1 = component(index);
        Component c3 = component(c1, index+2);
        if (c1 == c3) {
            c1.setBE24(index + c1.adjustment(), value);
        } else {
            setBE16(index    , (short) (value >>> 8));
            set8   (index + 2, (byte) value);
        }
    }

    public void setBE32(int index, int value) {
        Component c1 = component(index);
        Component c4 = component(c1, index+3);
        if (c1 == c4) {
            c1.setBE32(index + c1.adjustment(), value);
        } else {
            setBE16(index    , (short) (value >>> 16));
            setBE16(index + 2, (short) value);
        }
    }

    public void setBE48(int index, long value) {
        Component c1 = component(index);
        Component c6 = component(c1, index+5);
        if (c1 == c6) {
            c1.setBE48(index + c1.adjustment(), value);
        } else {
            setBE24(index    , (int) (value >>> 24));
            setBE24(index + 3, (int) value);
        }
    }

    public void setBE64(int index, long value) {
        Component c1 = component(index);
        Component c8 = component(c1, index+7);
        if (c1 == c8) {
            c1.setBE64(index + c1.adjustment(), value);
        } else {
            setBE32(index    , (int) (value >>> 32));
            setBE32(index + 4, (int) value);
        }
    }

    public void setLE16(int index, short value) {
        Component c1 = component(index);
        Component c2 = component(c1, index+1);
        if (c1 == c2) {
            c1.setLE16(index + c1.adjustment(), value);
        } else {
            c1.set8(index   + c1.adjustment(), (byte) (value >>> 0));
            c2.set8(index+1 + c2.adjustment(), (byte) (value >>> 8));
        }
    }

    public void setLE24(int index, int value) {
        Component c1 = component(index);
        Component c3 = component(c1, index+2);
        if (c1 == c3) {
            c1.setLE24(index + c1.adjustment(), value);
        } else {
            setLE16(index    , (short) value);
            set8   (index + 2, (byte) (value >>> 16));
        }
    }

    public void setLE32(int index, int value) {
        Component c1 = component(index);
        Component c4 = component(c1, index+3);
        if (c1 == c4) {
            c1.setLE32(index + c1.adjustment(), value);
        } else {
            setLE16(index    , (short) value);
            setLE16(index + 2, (short) (value >>> 16));
        }
    }

    public void setLE48(int index, long value) {
        Component c1 = component(index);
        Component c6 = component(c1, index+5);
        if (c1 == c6) {
            c1.setLE48(index + c1.adjustment(), value);
        } else {
            setLE24(index    , (int) value);
            setLE24(index + 3, (int) (value >>> 24));
        }
    }

    public void setLE64(int index, long value) {
        Component c1 = component(index);
        Component c8 = component(c1, index+7);
        if (c1 == c8) {
            c1.setLE64(index + c1.adjustment(), value);
        } else {
            setLE32(index    , (int) value);
            setLE32(index + 4, (int) (value >>> 32));
        }
    }

    public void set(int index, ByteArray src, int srcIndex, int length) {
        int remaining = length;
        Component c = component(index);
        if (remaining == 0) {
            return;
        }

        for (;;) {
            int localDstIndex = index + c.adjustment();
            int localLength = Math.min(remaining, c.globalEndIndex() - index);
            c.set(localDstIndex, src, srcIndex, localLength);

            srcIndex += localLength;
            index += localLength;
            remaining -= localLength;
            if (remaining == 0) {
                break;
            }
            c = c.next();
        }
    }

    public void set(int index, byte[] src, int srcIndex, int length) {
        int remaining = length;
        Component c = component(index);
        if (remaining == 0) {
            return;
        }

        for (;;) {
            int localDstIndex = index + c.adjustment();
            int localLength = Math.min(remaining, c.globalEndIndex() - index);
            c.set(localDstIndex, src, srcIndex, localLength);

            srcIndex += localLength;
            index += localLength;
            remaining -= localLength;
            if (remaining == 0) {
                break;
            }
            c = c.next();
        }
    }

    public void set(int index, ByteBuffer src) {
        int limit = src.limit();
        int remaining = src.remaining();
        Component c = component(index);
        if (remaining == 0) {
            return;
        }

        try {
            for (;;) {
                int localDstIndex = index + c.adjustment();
                int localLength = Math.min(remaining, c.globalEndIndex() - index);
                src.limit(src.position() + localLength);
                c.set(localDstIndex, src);

                index += localLength;
                remaining -= localLength;
                if (remaining == 0) {
                    break;
                }
                c = c.next();
            }
        } finally {
            src.limit(limit);
        }
    }

    public void copyTo(OutputStream out, int index, int length)
            throws IOException {
        int remaining = length;
        Component c = component(index);
        if (remaining == 0) {
            return;
        }

        for (;;) {
            int localSrcIndex = index + c.adjustment();
            int localLength = Math.min(remaining, c.globalEndIndex() - index);
            c.copyTo(out, localSrcIndex, localLength);
            index += localLength;
            remaining -= localLength;
            if (remaining == 0) {
                break;
            }
            c = c.next();
        }
    }

    @Override
    public int copyTo(WritableByteChannel out) throws IOException {
        // XXX Gathering write is not supported because of a known issue.
        //     See http://bugs.sun.com/view_bug.do?bug_id=6210541
        return out.write(getByteBuffer());
    }

    public int copyTo(WritableByteChannel out, int index, int length)
            throws IOException {

        if (count() > 1 && out instanceof GatheringByteChannel) {
            return copyTo((GatheringByteChannel) out, index, length);
        }

        int written = 0;
        int remaining = length;
        Component c = component(index);
        if (remaining == 0) {
            return 0;
        }

        for (;;) {
            int localSrcIndex = index + c.adjustment();
            int localLength = Math.min(remaining, c.globalEndIndex() - index);
            int localWritten = c.copyTo(out, localSrcIndex, localLength);
            if (localWritten == 0) {
                break;
            }
            written += localWritten;
            index += localLength;
            remaining -= localLength;
            if (remaining == 0) {
                break;
            }
            c = c.next();
        }

        return written;
    }

    @Override
    public int copyTo(GatheringByteChannel out) throws IOException {
        // XXX Gathering write is not supported because of a known issue.
        //     See http://bugs.sun.com/view_bug.do?bug_id=6210541
        return out.write(getByteBuffer());
    }

    @Override
    public int copyTo(GatheringByteChannel out, int index, int length)
            throws IOException {
        // XXX Gathering write is not supported because of a known issue.
        //     See http://bugs.sun.com/view_bug.do?bug_id=6210541
        return out.write(getByteBuffer(index, length));

        /* Disabled until gathering write is fixed
        int count = count();
        if (count < 2) {
            return copyTo((WritableByteChannel) out, index, length);
        }

        int remaining = length;
        Component c = component(index);
        if (remaining == 0) {
            return 0;
        }

        ByteBuffer[] buffers = new ByteBuffer[count];
        int bufferCount = 0;
        for (;;) {
            int localSrcIndex = index + c.adjustment();
            int localLength = Math.min(remaining, c.globalEndIndex() - index);
            buffers[bufferCount ++] = c.getByteBuffer(localSrcIndex, localLength);
            index += localLength;
            remaining -= localLength;
            if (remaining == 0) {
                break;
            }
            c = c.next();
        }

        return (int) out.write(buffers, 0, bufferCount);
        */
    }

    @Override
    public ByteBuffer getByteBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(length());
        Component c = head;
        for (;;) {
            buffer.put(c.getByteBuffer());
            c = c.next();
            if (c == null) {
                break;
            }
        }
        buffer.position(0);
        return buffer;
    }

    public ByteBuffer getByteBuffer(int index, int length) {
        ByteBuffer buffer = ByteBuffer.allocate(length());
        get(index, buffer);
        buffer.position(0);
        return buffer;
    }

    @Override
    public int hashCode() {
        Component c = head;
        int hashCode = c.hashCode();
        while ((c = c.next()) != null) {
            hashCode = hashCode * 31 + c.hashCode();
        }
        return hashCode;
    }

    public ByteArray read() {
        return removeFirst();
    }

    public ByteArray read(int length) {
        if (length < 0) {
            throw new IllegalArgumentException(
                    "length must be equal to or greater than 0.");
        }
        if (length == 0) {
            return ByteArray.EMPTY_BUFFER;
        }
        if (length > this.length) {
            throw new IllegalArgumentException(
                    "length should be equal to or less than " +
                    this.length + ".");
        }

        // Take care of the simple case first.
        Component current = head;
        if (current.length() >= length) {
            ByteArray a = current.unwrap();
            if (a.firstIndex() == current.firstIndex() &&
                a.length()     == length) {
                removeFirst();
            } else {
                a = new StaticPartialByteArray(
                        a, current.firstIndex(), length);
                current.firstIndex(current.firstIndex() + length);
            }
            return a;
        }

        // Now try to create a new CompositeByteArray.
        CompositeByteArray slice = new CompositeByteArray();
        int bytesToDiscard = length;
        for (;;) {
            current = head;
            int aLen = current.length();
            if (aLen > bytesToDiscard) {
                slice.addLast(new StaticPartialByteArray(
                        current.unwrap(), current.firstIndex(), bytesToDiscard));
                current.firstIndex(current.firstIndex() + bytesToDiscard);
                break;
            }

            removeFirst();
            ByteArray a = current.unwrap();
            if (a.firstIndex() == current.firstIndex() &&
                a.length()     == current.length()) {
                slice.addLast(a);
            } else {
                slice.addLast(new StaticPartialByteArray(
                        a, current.firstIndex(), current.length()));
            }

            if (aLen < bytesToDiscard) {
                bytesToDiscard -= aLen;
            } else { // aLen == bytesToDiscard
                break;
            }
        }

        return slice;
    }

    public void skip(int length) {
        if (length < 0) {
            throw new IllegalArgumentException(
                    "length must be equal to or greater than 0.");
        }
        if (length == 0) {
            return;
        }
        if (length > this.length) {
            throw new IllegalArgumentException(
                    "length should be equal to or less than " +
                    this.length + ".");
        }

        int bytesToDiscard = length;
        for (;;) {
            Component current = head;
            int aLen = current.length();
            if (aLen > bytesToDiscard) {
                current.firstIndex(current.firstIndex() + bytesToDiscard);
                break;
            }

            removeFirst();
            if (aLen < bytesToDiscard) {
                bytesToDiscard -= aLen;
            } else {
                break;
            }
        }
    }

    public byte read8() {
        Component first = (Component) first();
        byte value = first.get8(first.firstIndex());
        if (first.length() > 1) {
            first.firstIndex(first.firstIndex() + 1);
        } else {
            removeFirst();
        }
        return value;
    }

    public short readBE16() {
        Component first = (Component) first();
        short value;
        if (first.length() > 2) {
            value = first.getBE16(first.firstIndex());
            first.firstIndex(first.firstIndex() + 2);
        } else if (first.length() == 2) {
            value = first.getBE16(first.firstIndex());
            removeFirst();
        } else {
            value = getBE16(firstIndex());
            skip(2);
        }
        return value;
    }

    public int readBE24() {
        Component first = (Component) first();
        int value;
        if (first.length() > 3) {
            value = first.getBE24(first.firstIndex());
            first.firstIndex(first.firstIndex() + 3);
        } else if (first.length() == 3) {
            value = first.getBE24(first.firstIndex());
            removeFirst();
        } else {
            value = getBE24(firstIndex());
            skip(3);
        }
        return value;
    }

    public int readBE32() {
        Component first = (Component) first();
        int value;
        if (first.length() > 4) {
            value = first.getBE32(first.firstIndex());
            first.firstIndex(first.firstIndex() + 4);
        } else if (first.length() == 4) {
            value = first.getBE32(first.firstIndex());
            removeFirst();
        } else {
            value = getBE32(firstIndex());
            skip(4);
        }
        return value;
    }

    public long readBE48() {
        Component first = (Component) first();
        long value;
        if (first.length() > 6) {
            value = first.getBE48(first.firstIndex());
            first.firstIndex(first.firstIndex() + 6);
        } else if (first.length() == 6) {
            value = first.getBE48(first.firstIndex());
            removeFirst();
        } else {
            value = getBE48(firstIndex());
            skip(6);
        }
        return value;
    }

    public long readBE64() {
        Component first = (Component) first();
        long value;
        if (first.length() > 8) {
            value = first.getBE64(first.firstIndex());
            first.firstIndex(first.firstIndex() + 8);
        } else if (first.length() == 8) {
            value = first.getBE64(first.firstIndex());
            removeFirst();
        } else {
            value = getBE64(firstIndex());
            skip(8);
        }
        return value;
    }

    public short readLE16() {
        Component first = (Component) first();
        short value;
        if (first.length() > 2) {
            value = first.getLE16(first.firstIndex());
            first.firstIndex(first.firstIndex() + 2);
        } else if (first.length() == 2) {
            value = first.getLE16(first.firstIndex());
            removeFirst();
        } else {
            value = getLE16(firstIndex());
            skip(2);
        }
        return value;
    }

    public int readLE24() {
        Component first = (Component) first();
        int value;
        if (first.length() > 3) {
            value = first.getLE24(first.firstIndex());
            first.firstIndex(first.firstIndex() + 3);
        } else if (first.length() == 3) {
            value = first.getLE24(first.firstIndex());
            removeFirst();
        } else {
            value = getLE24(firstIndex());
            skip(3);
        }
        return value;
    }

    public int readLE32() {
        Component first = (Component) first();
        int value;
        if (first.length() > 4) {
            value = first.getLE32(first.firstIndex());
            first.firstIndex(first.firstIndex() + 4);
        } else if (first.length() == 4) {
            value = first.getLE32(first.firstIndex());
            removeFirst();
        } else {
            value = getLE32(firstIndex());
            skip(4);
        }
        return value;
    }

    public long readLE48() {
        Component first = (Component) first();
        long value;
        if (first.length() > 6) {
            value = first.getLE48(first.firstIndex());
            first.firstIndex(first.firstIndex() + 6);
        } else if (first.length() == 6) {
            value = first.getLE48(first.firstIndex());
            removeFirst();
        } else {
            value = getLE48(firstIndex());
            skip(6);
        }
        return value;
    }

    public long readLE64() {
        Component first = (Component) first();
        long value;
        if (first.length() > 8) {
            value = first.getLE64(first.firstIndex());
            first.firstIndex(first.firstIndex() + 8);
        } else if (first.length() == 8) {
            value = first.getLE64(first.firstIndex());
            removeFirst();
        } else {
            value = getLE64(firstIndex());
            skip(8);
        }
        return value;
    }

    public void write(byte[] src, int srcIndex, int length) {
        if (srcIndex == 0 && src.length == length) {
            addLast(new HeapByteArray(src));
        } else {
            addLast(new StaticPartialByteArray(src, srcIndex, length));
        }
    }

    public void write(byte[] src) {
        addLast(new HeapByteArray(src));
    }

    public void write(ByteArray src, int srcIndex, int length) {
        if (src.firstIndex() == srcIndex && src.length() == length) {
            addLast(src);
        } else {
            addLast(new StaticPartialByteArray(src, srcIndex, length));
        }
    }

    public void write(ByteArray src) {
        addLast(src);
    }

    public void write(ByteBuffer src) {
        ByteArray newArray;
        if (src.isReadOnly()) {
            newArray = new ByteBufferBackedByteArray(src);
        } else if (src.hasArray()) {
            byte[] a = src.array();
            if (src.arrayOffset() == 0 && src.position() == 0 &&
                src.limit() == src.capacity() && a.length == src.capacity()) {
                newArray = new HeapByteArray(a);
            } else {
                newArray = new StaticPartialByteArray(
                        a, src.position() + src.arrayOffset(), src.remaining());
            }
        } else if (src.isDirect()) {
            newArray = new ByteBufferBackedByteArray(src);
        } else {
            newArray = new ByteBufferBackedByteArray(src);
        }

        addLast(newArray);
    }

    public void write8(byte value) {
        Component tail = tailRoom(1);
        tail.set8(tail.endIndex() - 1, value);
    }

    public void writeBE16(short value) {
        Component tail = tailRoom(2);
        tail.setBE16(tail.endIndex() - 2, value);
    }

    public void writeBE24(int value) {
        Component tail = tailRoom(3);
        tail.setBE24(tail.endIndex() - 3, value);
    }

    public void writeBE32(int value) {
        Component tail = tailRoom(4);
        tail.setBE32(tail.endIndex() - 4, value);
    }

    public void writeBE48(long value) {
        Component tail = tailRoom(6);
        tail.setBE48(tail.endIndex() - 6, value);
    }

    public void writeBE64(long value) {
        Component tail = tailRoom(8);
        tail.setBE64(tail.endIndex() - 8, value);
    }

    public void writeLE16(short value) {
        Component tail = tailRoom(2);
        tail.setLE16(tail.endIndex() - 2, value);
    }

    public void writeLE24(int value) {
        Component tail = tailRoom(3);
        tail.setLE24(tail.endIndex() - 3, value);
    }

    public void writeLE32(int value) {
        Component tail = tailRoom(4);
        tail.setLE32(tail.endIndex() - 4, value);
    }

    public void writeLE48(long value) {
        Component tail = tailRoom(6);
        tail.setLE48(tail.endIndex() - 6, value);
    }

    public void writeLE64(long value) {
        Component tail = tailRoom(8);
        tail.setLE64(tail.endIndex() - 8, value);
    }

    public Iterator<ByteArray> iterator() {
        if (empty()) {
            return new Iterator<ByteArray>() {
                public boolean hasNext() {
                    return false;
                }

                public ByteArray next() {
                    throw new NoSuchElementException();
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        } else {
            return new Iterator<ByteArray>() {
                private final int expectedModCount = modCount;
                private boolean firstCall = true;
                private Component current = null;

                public boolean hasNext() {
                    return firstCall || current != null && current.next() != null;
                }

                public ByteArray next() {
                    if (modCount != expectedModCount) {
                        throw new ConcurrentModificationException();
                    }

                    if (firstCall) {
                        current = head;
                        firstCall = false;
                    } else if (current != null) {
                        current = current.next();
                    } else {
                        throw new NoSuchElementException();
                    }

                    if (current == null) {
                        throw new NoSuchElementException();
                    }

                    return current;
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    private Component component(int index) {
        return component(recentlyAccessed, index);
    }

    private Component component(Component head, int index) {
        if (index >= head.globalIndex() && index < head.globalEndIndex()) {
            recentlyAccessed = head;
            return head;
        }

        // Begin the search in both directions for the better chance of earlier finding.
        Component c1 = head.next();
        Component c2 = head.prev();
        for (;;) {
            if (c1 != null) {
                if (index >= c1.globalIndex() && index < c1.globalEndIndex()) {
                    recentlyAccessed = c1;
                    return c1;
                } else {
                    c1 = c1.next();
                }
            }

            if (c2 != null) {
                if (index >= c2.globalIndex() && index < c2.globalEndIndex()) {
                    recentlyAccessed = c2;
                    return c2;
                } else {
                    c2 = c2.prev();
                }
            }

            if (c1 == null && c2 == null) {
                throw new ArrayIndexOutOfBoundsException(index);
            }
        }
    }

    private class Component extends DynamicPartialByteArray {
        private Component next;
        private Component prev;
        private int globalIndex;
        private int globalEndIndex;
        private final int adjustment;

        public Component(Component prev, Component next, int index, ByteArray array) {
            super(array);

            length += array.length();

            firstIndex(array.firstIndex());
            length(array.length());

            this.prev = prev;
            this.next = next;
            globalIndex = index;
            globalEndIndex = index + array.length();
            adjustment = array.firstIndex() - index;

            if (next != null) {
                if (next.globalIndex() != globalEndIndex()) {
                    throw new IllegalStateException("underflow");
                }
            } else if (prev != null && prev.globalEndIndex() != globalIndex()) {
                throw new IllegalStateException("overflow");
            }
        }

        @Override
        public void firstIndex(int firstIndex) {
            assert prev() == null;

            int diff = firstIndex - firstIndex();
            super.firstIndex(firstIndex);
            globalIndex += diff;
            length -= diff;
        }

        @Override
        public void length(int length) {
            assert next() == null;

            int diff = length - length();
            super.length(length);
            globalEndIndex += diff;
            CompositeByteArray.this.length += diff;
        }

        public Component prev() {
            return prev;
        }

        public void prev(Component prev) {
            this.prev = prev;
        }

        public Component next() {
            return next;
        }

        public void next(Component next) {
            this.next = next;
        }

        public int globalIndex() {
            return globalIndex;
        }

        public int globalEndIndex() {
            return globalEndIndex;
        }

        public int adjustment() {
            return adjustment;
        }
    }

    private Component tailRoom(int length) {
        Component oldTailRoom = tailRoom;
        if (oldTailRoom == null || oldTailRoom.unwrap().endIndex() - oldTailRoom.endIndex() < length) {
            // TODO Needs ByteArrayFactory.
            ByteArray newTail = new HeapByteArray(capacityIncrement);
            addLast(newTail);

            // Set the length of the tail to the minimum.
            // Each write operation will increase the length.
            Component newDynamicTailRoom = (Component) last();
            newDynamicTailRoom.length(length);

            tailRoom = newDynamicTailRoom;
            return newDynamicTailRoom;
        } else {
            oldTailRoom.length(oldTailRoom.length() + length);
            return oldTailRoom;
        }
    }
}
