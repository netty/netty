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
import java.nio.channels.WritableByteChannel;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;


public class CompositeByteArray extends AbstractByteArray implements Iterable<ByteArray> {

    Component head;
    private Component tail;
    // Cache recently accessed component for performance boost
    private Component recentlyAccessed;
    int length;
    private int count;
    int modCount;

    public CompositeByteArray() {
        init(ByteArray.EMPTY_BUFFER);
    }

    public CompositeByteArray(ByteArray array) {
        if (array == null) {
            throw new NullPointerException("array");
        }
        init(array);
    }

    public CompositeByteArray(ByteArray firstArray, ByteArray... otherArrays) {
        if (firstArray == null) {
            throw new NullPointerException("firstArray");
        }
        if (otherArrays == null) {
            throw new NullPointerException("otherArrays");
        }
        init(firstArray);
        for (int i = 0; i < otherArrays.length; i ++) {
            ByteArray a = otherArrays[i];
            if (a == null) {
                throw new NullPointerException("otherArrays[" + i + "]");
            }
            addLast(a);
        }
    }

    private void init(ByteArray array) {
        length = 0;
        recentlyAccessed = head = tail = new Component(null, null, 0, array);
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

        if (length == 0) {
            init(array);
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
                addFirst((ByteArray) a);
            }
            return;
        }

        if (empty()) {
            init(array);
            return;
        }

        Component oldTail = tail;
        Component newTail = new Component(oldTail, null, oldTail.globalIndex() + oldTail.length(), array);
        oldTail.next(newTail);

        // Unlike removeFirst/removeLast, length is modified when a new
        // Component is created.
        //length += array.length();

        tail = newTail;
        count ++;
        modCount ++;
    }

    public ByteArray removeFirst() {
        ensurePositiveLength();
        if (head == tail) {
            ByteArray first = head;
            init(EMPTY_BUFFER);
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
            init(EMPTY_BUFFER);
            return last;
        }

        Component oldTail = tail;
        Component newTail = tail.prev();
        newTail.next(null);

        tail = newTail;
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

    public ByteArray removeFirst(int length) {
        if (length < 0) {
            throw new IllegalArgumentException(
                    "length must be equal to or greater than 0.");
        }
        if (length > this.length) {
            throw new IllegalArgumentException(
                    "length should be equal to or less than " +
                    this.length + ".");
        }
        if (length == 0) {
            return ByteArray.EMPTY_BUFFER;
        }

        // Take care of the simple case first.
        Component current = head;
        if (current.length() >= length) {
            ByteArray a = current.unwrap();
            if (a.firstIndex() == current.firstIndex() &&
                a.length()     == current.length()) {
                return a;
            } else {
                return new PartialByteArray(
                        a, current.firstIndex(), current.length());
            }
        }

        // Now try to create a new CompositeByteArray.
        CompositeByteArray slice = new CompositeByteArray();
        int bytesToDiscard = length;
        for (;;) {
            current = head;
            int aLen = current.length();
            if (aLen > bytesToDiscard) {
                slice.addLast(new PartialByteArray(current.unwrap(), current.firstIndex(), bytesToDiscard));
                current.firstIndex(current.firstIndex() + bytesToDiscard);
                break;
            }

            removeFirst();
            ByteArray a = current.unwrap();
            if (a.firstIndex() == current.firstIndex() &&
                a.length()     == current.length()) {
                slice.addLast(a);
            } else {
                slice.addLast(new PartialByteArray(
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

    public void discardFirst(int length) {
        if (length < 0) {
            throw new IllegalArgumentException(
                    "length must be equal to or greater than 0.");
        }
        if (length > this.length) {
            throw new IllegalArgumentException(
                    "length should be equal to or less than " +
                    this.length + ".");
        }
        if (length == 0) {
            return;
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

        Component c2 = component(c1, index+1);
        return (c1.get8(index   + c1.adjustment()) & 0xff) << 16 |
               (c2.get8(index+1 + c2.adjustment()) & 0xff) <<  8 |
               (c3.get8(index+2 + c3.adjustment()) & 0xff) <<  0;
    }

    public int getBE32(int index) {
        Component c1 = component(index);
        Component c4 = component(c1, index+3);
        if (c1 == c4) {
            return c1.getBE32(index + c1.adjustment());
        }

        Component c2 = component(c1, index+1);
        Component c3 = component(c2, index+2);
        return (c1.get8(index   + c1.adjustment()) & 0xff) << 24 |
               (c2.get8(index+1 + c2.adjustment()) & 0xff) << 16 |
               (c3.get8(index+2 + c3.adjustment()) & 0xff) <<  8 |
               (c4.get8(index+3 + c4.adjustment()) & 0xff) <<  0;
    }

    public long getBE48(int index) {
        Component c1 = component(index);
        Component c6 = component(c1, index+5);
        if (c1 == c6) {
            return c1.getBE48(index + c1.adjustment());
        }

        Component c2 = component(c1, index+1);
        Component c3 = component(c2, index+2);
        Component c4 = component(c3, index+3);
        Component c5 = component(c4, index+4);
        return ((long) c1.get8(index   + c1.adjustment()) & 0xff) << 40 |
               ((long) c2.get8(index+1 + c2.adjustment()) & 0xff) << 32 |
               ((long) c3.get8(index+2 + c3.adjustment()) & 0xff) << 24 |
               ((long) c4.get8(index+3 + c4.adjustment()) & 0xff) << 16 |
               ((long) c5.get8(index+4 + c5.adjustment()) & 0xff) <<  8 |
               ((long) c6.get8(index+5 + c6.adjustment()) & 0xff) <<  0;
    }

    public long getBE64(int index) {
        Component c1 = component(index);
        Component c8 = component(c1, index+7);
        if (c1 == c8) {
            return c1.getBE64(index + c1.adjustment());
        }

        Component c2 = component(c1, index+1);
        Component c3 = component(c2, index+2);
        Component c4 = component(c3, index+3);
        Component c5 = component(c4, index+4);
        Component c6 = component(c5, index+5);
        Component c7 = component(c6, index+6);
        return ((long) c1.get8(index   + c1.adjustment()) & 0xff) << 56 |
               ((long) c2.get8(index+1 + c2.adjustment()) & 0xff) << 48 |
               ((long) c3.get8(index+2 + c3.adjustment()) & 0xff) << 40 |
               ((long) c4.get8(index+3 + c4.adjustment()) & 0xff) << 32 |
               ((long) c5.get8(index+4 + c5.adjustment()) & 0xff) << 24 |
               ((long) c6.get8(index+5 + c6.adjustment()) & 0xff) << 16 |
               ((long) c7.get8(index+6 + c7.adjustment()) & 0xff) <<  8 |
               ((long) c8.get8(index+7 + c8.adjustment()) & 0xff) <<  0;
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

        Component c2 = component(c1, index+1);
        return (c1.get8(index   + c1.adjustment()) & 0xff) <<  0 |
               (c2.get8(index+1 + c2.adjustment()) & 0xff) <<  8 |
               (c3.get8(index+2 + c3.adjustment()) & 0xff) << 16;
    }

    public int getLE32(int index) {
        Component c1 = component(index);
        Component c4 = component(c1, index+3);
        if (c1 == c4) {
            return c1.getLE32(index + c1.adjustment());
        }

        Component c2 = component(c1, index+1);
        Component c3 = component(c2, index+2);
        return (c1.get8(index   + c1.adjustment()) & 0xff) <<  0 |
               (c2.get8(index+1 + c2.adjustment()) & 0xff) <<  8 |
               (c3.get8(index+2 + c3.adjustment()) & 0xff) << 16 |
               (c4.get8(index+3 + c4.adjustment()) & 0xff) << 24;
    }

    public long getLE48(int index) {
        Component c1 = component(index);
        Component c6 = component(c1, index+5);
        if (c1 == c6) {
            return c1.getLE48(index + c1.adjustment());
        }

        Component c2 = component(c1, index+1);
        Component c3 = component(c2, index+2);
        Component c4 = component(c3, index+3);
        Component c5 = component(c4, index+4);
        return ((long) c1.get8(index   + c1.adjustment()) & 0xff) <<  0 |
               ((long) c2.get8(index+1 + c2.adjustment()) & 0xff) <<  8 |
               ((long) c3.get8(index+2 + c3.adjustment()) & 0xff) << 16 |
               ((long) c4.get8(index+3 + c4.adjustment()) & 0xff) << 24 |
               ((long) c5.get8(index+4 + c5.adjustment()) & 0xff) << 32 |
               ((long) c6.get8(index+5 + c6.adjustment()) & 0xff) << 40;
    }

    public long getLE64(int index) {
        Component c1 = component(index);
        Component c8 = component(c1, index+7);
        if (c1 == c8) {
            return c1.getLE64(index + c1.adjustment());
        }

        Component c2 = component(c1, index+1);
        Component c3 = component(c2, index+2);
        Component c4 = component(c3, index+3);
        Component c5 = component(c4, index+4);
        Component c6 = component(c5, index+5);
        Component c7 = component(c6, index+6);
        return ((long) c1.get8(index   + c1.adjustment()) & 0xff) <<  0 |
               ((long) c2.get8(index+1 + c2.adjustment()) & 0xff) <<  8 |
               ((long) c3.get8(index+2 + c3.adjustment()) & 0xff) << 16 |
               ((long) c4.get8(index+3 + c4.adjustment()) & 0xff) << 24 |
               ((long) c5.get8(index+4 + c5.adjustment()) & 0xff) << 32 |
               ((long) c6.get8(index+5 + c6.adjustment()) & 0xff) << 40 |
               ((long) c7.get8(index+6 + c7.adjustment()) & 0xff) << 48 |
               ((long) c8.get8(index+7 + c8.adjustment()) & 0xff) << 56;
    }

    public void get(int index, ByteArray dst, int dstIndex, int length) {
        int remaining = length;
        Component c = component(index);

        while (remaining > 0) {
            int localSrcIndex = index + c.adjustment();
            int localLength = Math.min(remaining, c.globalEndIndex() - index);
            c.get(localSrcIndex, dst, dstIndex, localLength);

            index += localLength;
            dstIndex += localLength;
            remaining -= localLength;
            c = component(c, index);
        }
    }

    public void get(int index, byte[] dst, int dstIndex, int length) {
        int remaining = length;
        Component c = component(index);

        while (remaining > 0) {
            int localSrcIndex = index + c.adjustment();
            int localLength = Math.min(remaining, c.globalEndIndex() - index);
            c.get(localSrcIndex, dst, dstIndex, localLength);

            index += localLength;
            dstIndex += localLength;
            remaining -= localLength;
            c = component(c, index);
        }
    }

    public void get(int index, ByteBuffer dst) {
        int limit = dst.limit();
        int remaining = dst.remaining();
        Component c = component(index);

        try {
            while (remaining > 0) {
                int localSrcIndex = index + c.adjustment();
                int localLength = Math.min(remaining, c.globalEndIndex() - index);
                dst.limit(dst.position() + localLength);
                c.get(localSrcIndex, dst);

                index += localLength;
                remaining -= localLength;
                c = component(c, index);
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
            Component c2 = component(c1, index+1);
            c1.set8(index   + c1.adjustment(), (byte) (value >>> 16));
            c2.set8(index+1 + c2.adjustment(), (byte) (value >>>  8));
            c3.set8(index+2 + c3.adjustment(), (byte) (value >>>  0));
        }
    }

    public void setBE32(int index, int value) {
        Component c1 = component(index);
        Component c4 = component(c1, index+3);
        if (c1 == c4) {
            c1.setBE32(index + c1.adjustment(), value);
        } else {
            Component c2 = component(c1, index+1);
            Component c3 = component(c2, index+2);
            c1.set8(index   + c1.adjustment(), (byte) (value >>> 24));
            c2.set8(index+1 + c2.adjustment(), (byte) (value >>> 16));
            c3.set8(index+2 + c3.adjustment(), (byte) (value >>>  8));
            c4.set8(index+3 + c4.adjustment(), (byte) (value >>>  0));
        }
    }

    public void setBE48(int index, long value) {
        Component c1 = component(index);
        Component c6 = component(c1, index+5);
        if (c1 == c6) {
            c1.setBE48(index + c1.adjustment(), value);
        } else {
            Component c2 = component(c1, index+1);
            Component c3 = component(c2, index+2);
            Component c4 = component(c3, index+3);
            Component c5 = component(c4, index+4);
            c1.set8(index   + c1.adjustment(), (byte) (value >>> 40));
            c2.set8(index+1 + c2.adjustment(), (byte) (value >>> 32));
            c3.set8(index+2 + c3.adjustment(), (byte) (value >>> 24));
            c4.set8(index+3 + c4.adjustment(), (byte) (value >>> 16));
            c5.set8(index+4 + c5.adjustment(), (byte) (value >>>  8));
            c6.set8(index+5 + c6.adjustment(), (byte) (value >>>  0));
        }
    }

    public void setBE64(int index, long value) {
        Component c1 = component(index);
        Component c8 = component(c1, index+7);
        if (c1 == c8) {
            c1.setBE64(index + c1.adjustment(), value);
        } else {
            Component c2 = component(c1, index+1);
            Component c3 = component(c2, index+2);
            Component c4 = component(c3, index+3);
            Component c5 = component(c4, index+4);
            Component c6 = component(c5, index+5);
            Component c7 = component(c6, index+6);
            c1.set8(index   + c1.adjustment(), (byte) (value >>> 56));
            c2.set8(index+1 + c2.adjustment(), (byte) (value >>> 48));
            c3.set8(index+2 + c3.adjustment(), (byte) (value >>> 40));
            c4.set8(index+3 + c4.adjustment(), (byte) (value >>> 32));
            c5.set8(index+4 + c5.adjustment(), (byte) (value >>> 24));
            c6.set8(index+5 + c6.adjustment(), (byte) (value >>> 16));
            c7.set8(index+6 + c7.adjustment(), (byte) (value >>>  8));
            c8.set8(index+7 + c8.adjustment(), (byte) (value >>>  0));
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
            Component c2 = component(c1, index+1);
            c1.set8(index   + c1.adjustment(), (byte) (value >>>  0));
            c2.set8(index+1 + c2.adjustment(), (byte) (value >>>  8));
            c3.set8(index+2 + c3.adjustment(), (byte) (value >>> 16));
        }
    }

    public void setLE32(int index, int value) {
        Component c1 = component(index);
        Component c4 = component(c1, index+3);
        if (c1 == c4) {
            c1.setLE32(index + c1.adjustment(), value);
        } else {
            Component c2 = component(c1, index+1);
            Component c3 = component(c2, index+2);
            c1.set8(index   + c1.adjustment(), (byte) (value >>>  0));
            c2.set8(index+1 + c2.adjustment(), (byte) (value >>>  8));
            c3.set8(index+2 + c3.adjustment(), (byte) (value >>> 16));
            c4.set8(index+3 + c4.adjustment(), (byte) (value >>> 24));
        }
    }

    public void setLE48(int index, long value) {
        Component c1 = component(index);
        Component c6 = component(c1, index+5);
        if (c1 == c6) {
            c1.setLE48(index + c1.adjustment(), value);
        } else {
            Component c2 = component(c1, index+1);
            Component c3 = component(c2, index+2);
            Component c4 = component(c3, index+3);
            Component c5 = component(c4, index+4);
            c1.set8(index   + c1.adjustment(), (byte) (value >>>  0));
            c2.set8(index+1 + c2.adjustment(), (byte) (value >>>  8));
            c3.set8(index+2 + c3.adjustment(), (byte) (value >>> 16));
            c4.set8(index+3 + c4.adjustment(), (byte) (value >>> 24));
            c5.set8(index+4 + c5.adjustment(), (byte) (value >>> 32));
            c6.set8(index+5 + c6.adjustment(), (byte) (value >>> 40));
        }
    }

    public void setLE64(int index, long value) {
        Component c1 = component(index);
        Component c8 = component(c1, index+7);
        if (c1 == c8) {
            c1.setLE64(index + c1.adjustment(), value);
        } else {
            Component c2 = component(c1, index+1);
            Component c3 = component(c2, index+2);
            Component c4 = component(c3, index+3);
            Component c5 = component(c4, index+4);
            Component c6 = component(c5, index+5);
            Component c7 = component(c6, index+6);
            c1.set8(index   + c1.adjustment(), (byte) (value >>>  0));
            c2.set8(index+1 + c2.adjustment(), (byte) (value >>>  8));
            c3.set8(index+2 + c3.adjustment(), (byte) (value >>> 16));
            c4.set8(index+3 + c4.adjustment(), (byte) (value >>> 24));
            c5.set8(index+4 + c5.adjustment(), (byte) (value >>> 32));
            c6.set8(index+5 + c6.adjustment(), (byte) (value >>> 40));
            c7.set8(index+6 + c7.adjustment(), (byte) (value >>> 48));
            c8.set8(index+7 + c8.adjustment(), (byte) (value >>> 56));
        }
    }

    public void set(int index, ByteArray src, int srcIndex, int length) {
        int remaining = length;
        Component c = component(index);

        while (remaining > 0) {
            int localDstIndex = index + c.adjustment();
            int localLength = Math.min(remaining, c.globalEndIndex() - index);
            c.set(localDstIndex, src, srcIndex, localLength);

            srcIndex += localLength;
            index += localLength;
            remaining -= localLength;
            c = component(c, index);
        }
    }

    public void set(int index, byte[] src, int srcIndex, int length) {
        int remaining = length;
        Component c = component(index);

        while (remaining > 0) {

            int localDstIndex = index + c.adjustment();
            int localLength = Math.min(remaining, c.globalEndIndex() - index);
            c.set(localDstIndex, src, srcIndex, localLength);

            srcIndex += localLength;
            index += localLength;
            remaining -= localLength;
            c = component(c, index);
        }
    }

    public void set(int index, ByteBuffer src) {
        int limit = src.limit();
        int remaining = src.remaining();
        Component c = component(index);

        try {
            while (remaining > 0) {
                int localDstIndex = index + c.adjustment();
                int localLength = Math.min(remaining, c.globalEndIndex() - index);
                src.limit(src.position() + localLength);
                c.set(localDstIndex, src);

                index += localLength;
                remaining -= localLength;
                c = component(c, index);
            }
        } finally {
            src.limit(limit);
        }
    }

    public void copyTo(OutputStream out, int index, int length)
            throws IOException {
        int remaining = length;
        Component c = component(index);
        while (remaining > 0) {
            int localSrcIndex = index + c.adjustment();
            int localLength = Math.min(remaining, c.globalEndIndex() - index);
            c.copyTo(out, localSrcIndex, localLength);
            index += localLength;
            remaining -= localLength;
            c = component(c, index);
        }
    }

    public int copyTo(WritableByteChannel out, int index, int length)
            throws IOException {
        int written = 0;
        int remaining = length;
        Component c = component(index);
        while (remaining > 0) {
            int localSrcIndex = index + c.adjustment();
            int localLength = Math.min(remaining, c.globalEndIndex() - index);
            int localWritten = c.copyTo(out, localSrcIndex, localLength);
            if (localWritten == 0) {
                break;
            }
            written += localWritten;
            index += localLength;
            remaining -= localLength;
            c = component(c, index);
        }

        return written;
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
            } else if (prev != null) {
                if (prev.globalEndIndex() != globalIndex()) {
                    throw new IllegalStateException("overflow");
                }
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
}
