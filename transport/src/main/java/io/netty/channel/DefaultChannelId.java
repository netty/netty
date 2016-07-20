/*
 * Copyright 2013 The Netty Project
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

package io.netty.channel;

import io.netty.buffer.ByteBufUtil;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.MacAddressUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.ThreadLocalRandom;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * The default {@link ChannelId} implementation.
 */
public final class DefaultChannelId implements ChannelId {

    private static final long serialVersionUID = 3884076183504074063L;

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultChannelId.class);

    private static final Pattern MACHINE_ID_PATTERN = Pattern.compile("^(?:[0-9a-fA-F][:-]?){6,8}$");
    private static final int MACHINE_ID_LEN = MacAddressUtil.MAC_ADDRESS_LENGTH;
    private static final byte[] MACHINE_ID;
    private static final int PROCESS_ID_LEN = 4;
    // Maximal value for 64bit systems is 2^22.  See man 5 proc.
    // See https://github.com/netty/netty/issues/2706
    private static final int MAX_PROCESS_ID = 4194304;
    private static final int PROCESS_ID;
    private static final int SEQUENCE_LEN = 4;
    private static final int TIMESTAMP_LEN = 8;
    private static final int RANDOM_LEN = 4;

    private static final AtomicInteger nextSequence = new AtomicInteger();

    /**
     * Returns a new {@link DefaultChannelId} instance.
     */
    public static DefaultChannelId newInstance() {
        DefaultChannelId id = new DefaultChannelId();
        id.init();
        return id;
    }

    static {
        int processId = -1;
        String customProcessId = SystemPropertyUtil.get("io.netty.processId");
        if (customProcessId != null) {
            try {
                processId = Integer.parseInt(customProcessId);
            } catch (NumberFormatException e) {
                // Malformed input.
            }

            if (processId < 0 || processId > MAX_PROCESS_ID) {
                processId = -1;
                logger.warn("-Dio.netty.processId: {} (malformed)", customProcessId);
            } else if (logger.isDebugEnabled()) {
                logger.debug("-Dio.netty.processId: {} (user-set)", processId);
            }
        }

        if (processId < 0) {
            processId = defaultProcessId();
            if (logger.isDebugEnabled()) {
                logger.debug("-Dio.netty.processId: {} (auto-detected)", processId);
            }
        }

        PROCESS_ID = processId;

        byte[] machineId = null;
        String customMachineId = SystemPropertyUtil.get("io.netty.machineId");
        if (customMachineId != null) {
            if (MACHINE_ID_PATTERN.matcher(customMachineId).matches()) {
                machineId = parseMachineId(customMachineId);
                logger.debug("-Dio.netty.machineId: {} (user-set)", customMachineId);
            } else {
                logger.warn("-Dio.netty.machineId: {} (malformed)", customMachineId);
            }
        }

        if (machineId == null) {
            machineId = defaultMachineId();
            if (logger.isDebugEnabled()) {
                logger.debug("-Dio.netty.machineId: {} (auto-detected)", MacAddressUtil.formatAddress(machineId));
            }
        }

        MACHINE_ID = machineId;
    }

    @SuppressWarnings("DynamicRegexReplaceableByCompiledPattern")
    private static byte[] parseMachineId(String value) {
        // Strip separators.
        value = value.replaceAll("[:-]", "");

        byte[] machineId = new byte[MACHINE_ID_LEN];
        for (int i = 0; i < value.length(); i += 2) {
            machineId[i] = (byte) Integer.parseInt(value.substring(i, i + 2), 16);
        }

        return machineId;
    }

    private static byte[] defaultMachineId() {
        byte[] bestMacAddr = MacAddressUtil.bestAvailableMac();
        if (bestMacAddr == null) {
            bestMacAddr = new byte[MacAddressUtil.MAC_ADDRESS_LENGTH];
            ThreadLocalRandom.current().nextBytes(bestMacAddr);
            logger.warn(
                    "Failed to find a usable hardware address from the network interfaces; using random bytes: {}",
                    MacAddressUtil.formatAddress(bestMacAddr));
        }
        return bestMacAddr;
    }

    private static int defaultProcessId() {
        final ClassLoader loader = PlatformDependent.getClassLoader(DefaultChannelId.class);
        String value;
        try {
            // Invoke java.lang.management.ManagementFactory.getRuntimeMXBean().getName()
            Class<?> mgmtFactoryType = Class.forName("java.lang.management.ManagementFactory", true, loader);
            Class<?> runtimeMxBeanType = Class.forName("java.lang.management.RuntimeMXBean", true, loader);

            Method getRuntimeMXBean = mgmtFactoryType.getMethod("getRuntimeMXBean", EmptyArrays.EMPTY_CLASSES);
            Object bean = getRuntimeMXBean.invoke(null, EmptyArrays.EMPTY_OBJECTS);
            Method getName = runtimeMxBeanType.getMethod("getName", EmptyArrays.EMPTY_CLASSES);
            value = (String) getName.invoke(bean, EmptyArrays.EMPTY_OBJECTS);
        } catch (Exception e) {
            logger.debug("Could not invoke ManagementFactory.getRuntimeMXBean().getName(); Android?", e);
            try {
                // Invoke android.os.Process.myPid()
                Class<?> processType = Class.forName("android.os.Process", true, loader);
                Method myPid = processType.getMethod("myPid", EmptyArrays.EMPTY_CLASSES);
                value = myPid.invoke(null, EmptyArrays.EMPTY_OBJECTS).toString();
            } catch (Exception e2) {
                logger.debug("Could not invoke Process.myPid(); not Android?", e2);
                value = "";
            }
        }

        int atIndex = value.indexOf('@');
        if (atIndex >= 0) {
            value = value.substring(0, atIndex);
        }

        int pid;
        try {
            pid = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // value did not contain an integer.
            pid = -1;
        }

        if (pid < 0 || pid > MAX_PROCESS_ID) {
            pid = ThreadLocalRandom.current().nextInt(MAX_PROCESS_ID + 1);
            logger.warn("Failed to find the current process ID from '{}'; using a random value: {}",  value, pid);
        }

        return pid;
    }

    private final byte[] data = new byte[MACHINE_ID_LEN + PROCESS_ID_LEN + SEQUENCE_LEN + TIMESTAMP_LEN + RANDOM_LEN];
    private int hashCode;

    private transient String shortValue;
    private transient String longValue;

    private DefaultChannelId() { }

    private void init() {
        int i = 0;

        // machineId
        System.arraycopy(MACHINE_ID, 0, data, i, MACHINE_ID_LEN);
        i += MACHINE_ID_LEN;

        // processId
        i = writeInt(i, PROCESS_ID);

        // sequence
        i = writeInt(i, nextSequence.getAndIncrement());

        // timestamp (kind of)
        i = writeLong(i, Long.reverse(System.nanoTime()) ^ System.currentTimeMillis());

        // random
        int random = ThreadLocalRandom.current().nextInt();
        hashCode = random;
        i = writeInt(i, random);

        assert i == data.length;
    }

    private int writeInt(int i, int value) {
        data[i ++] = (byte) (value >>> 24);
        data[i ++] = (byte) (value >>> 16);
        data[i ++] = (byte) (value >>> 8);
        data[i ++] = (byte) value;
        return i;
    }

    private int writeLong(int i, long value) {
        data[i ++] = (byte) (value >>> 56);
        data[i ++] = (byte) (value >>> 48);
        data[i ++] = (byte) (value >>> 40);
        data[i ++] = (byte) (value >>> 32);
        data[i ++] = (byte) (value >>> 24);
        data[i ++] = (byte) (value >>> 16);
        data[i ++] = (byte) (value >>> 8);
        data[i ++] = (byte) value;
        return i;
    }

    @Override
    public String asShortText() {
        String shortValue = this.shortValue;
        if (shortValue == null) {
            this.shortValue = shortValue = ByteBufUtil.hexDump(
                    data, MACHINE_ID_LEN + PROCESS_ID_LEN + SEQUENCE_LEN + TIMESTAMP_LEN, RANDOM_LEN);
        }
        return shortValue;
    }

    @Override
    public String asLongText() {
        String longValue = this.longValue;
        if (longValue == null) {
            this.longValue = longValue = newLongValue();
        }
        return longValue;
    }

    private String newLongValue() {
        StringBuilder buf = new StringBuilder(2 * data.length + 5);
        int i = 0;
        i = appendHexDumpField(buf, i, MACHINE_ID_LEN);
        i = appendHexDumpField(buf, i, PROCESS_ID_LEN);
        i = appendHexDumpField(buf, i, SEQUENCE_LEN);
        i = appendHexDumpField(buf, i, TIMESTAMP_LEN);
        i = appendHexDumpField(buf, i, RANDOM_LEN);
        assert i == data.length;
        return buf.substring(0, buf.length() - 1);
    }

    private int appendHexDumpField(StringBuilder buf, int i, int length) {
        buf.append(ByteBufUtil.hexDump(data, i, length));
        buf.append('-');
        i += length;
        return i;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public int compareTo(ChannelId o) {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof DefaultChannelId)) {
            return false;
        }

        return Arrays.equals(data, ((DefaultChannelId) obj).data);
    }

    @Override
    public String toString() {
        return asShortText();
    }
}
