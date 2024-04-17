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

package io.netty5.channel;

import io.netty5.util.internal.MacAddressUtil;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.SystemPropertyUtil;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty5.util.internal.MacAddressUtil.defaultMachineId;
import static io.netty5.util.internal.MacAddressUtil.parseMAC;

/**
 * The default {@link ChannelId} implementation.
 */
public final class DefaultChannelId implements ChannelId {

    private static final long serialVersionUID = -9213733333618193529L;

    private static final Logger logger = LoggerFactory.getLogger(DefaultChannelId.class);

    private static final byte[] MACHINE_ID;

    private static final int PROCESS_ID;

    private static final AtomicInteger nextSequence = new AtomicInteger();

    /**
     * Returns a new {@link DefaultChannelId} instance.
     */
    public static DefaultChannelId newInstance() {
        return new DefaultChannelId(
                MACHINE_ID,
                PROCESS_ID,
                nextSequence.getAndIncrement(),
                Long.reverse(System.nanoTime()) ^ System.currentTimeMillis(),
                ThreadLocalRandom.current().nextInt()
        );
    }

    static {
        int processId = -1;
        String customProcessId = SystemPropertyUtil.get("io.netty5.processId");
        if (customProcessId != null) {
            try {
                processId = Integer.parseInt(customProcessId);
            } catch (NumberFormatException e) {
                // Malformed input.
            }

            if (processId < 0) {
                processId = -1;
                logger.warn("-Dio.netty5.processId: {} (malformed)", customProcessId);
            } else if (logger.isDebugEnabled()) {
                logger.debug("-Dio.netty5.processId: {} (user-set)", processId);
            }
        }

        if (processId < 0) {
            processId = defaultProcessId();
            if (logger.isDebugEnabled()) {
                logger.debug("-Dio.netty5.processId: {} (auto-detected)", processId);
            }
        }

        PROCESS_ID = processId;

        byte[] machineId = null;
        String customMachineId = SystemPropertyUtil.get("io.netty5.machineId");
        if (customMachineId != null) {
            try {
                machineId = parseMAC(customMachineId);
            } catch (Exception e) {
                logger.warn("-Dio.netty5.machineId: {} (malformed)", customMachineId, e);
            }
            if (machineId != null) {
                logger.debug("-Dio.netty5.machineId: {} (user-set)", customMachineId);
            }
        }

        if (machineId == null) {
            machineId = defaultMachineId();
            if (logger.isDebugEnabled()) {
                logger.debug("-Dio.netty5.machineId: {} (auto-detected)", MacAddressUtil.formatAddress(machineId));
            }
        }

        MACHINE_ID = machineId;
    }

    private static int defaultProcessId() {
        long pid = ProcessHandle.current().pid();
        if (pid > Integer.MAX_VALUE || pid < Integer.MIN_VALUE) {
            logger.warn("Current process ID exceeds int range: " + pid);
            pid = ThreadLocalRandom.current().nextInt();
        }
        return (int) pid;
    }

    private final byte[] machineId;

    private final int processId;

    private final int sequenceId;

    private final long mixedTimeStampId;

    private final int randomId;

    private final int hashCode;

    private transient String shortValue;

    private transient String longValue;

    // Package-private for testing
    @VisibleForTesting
    DefaultChannelId(final byte[] machineId, final int processId, final int sequenceId,
                             final long mixedTimeStampId, final int randomId) {
        this.machineId = machineId;
        this.processId = processId;
        this.sequenceId = sequenceId;
        this.mixedTimeStampId = mixedTimeStampId;
        this.randomId = randomId;
        int hash = Arrays.hashCode(machineId);
        hash = hash * 31 + processId;
        hash = hash * 31 + sequenceId;
        hash = hash * 31 + Long.hashCode(mixedTimeStampId);
        hash = hash * 31 + randomId;
        hashCode = hash; // cache the hashCode since no field is mutable.
    }

    @Override
    public String asShortText() {
        String shortValue = this.shortValue;
        if (shortValue == null) {
            this.shortValue = shortValue = newShortValue();
        }
        return shortValue;
    }

    private String newShortValue() {
        final StringBuilder buf = new StringBuilder(Integer.BYTES * 2);
        intToHexStringPadded(buf, randomId);
        return buf.toString();
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
        final byte[] machineId = this.machineId;
        final int machineIdLen = machineId.length;
        final StringBuilder buf = new StringBuilder(
                (
                        machineIdLen + // machineId length
                        Integer.BYTES + // processId length
                        Integer.BYTES +  // sequenceId length
                        Long.BYTES +  // mixedTimeStampId length
                        Integer.BYTES  // randomId length
                ) * 2 + 4);
        StringUtil.toHexStringPadded(buf, machineId, 0, machineIdLen);
        buf.append('-');
        intToHexStringPadded(buf, processId);
        buf.append('-');
        intToHexStringPadded(buf, sequenceId);
        buf.append('-');
        longToHexStringPadded(buf, mixedTimeStampId);
        buf.append('-');
        intToHexStringPadded(buf, randomId);
        return buf.toString();
    }

    private static void intToHexStringPadded(final StringBuilder buf, final int value) {
        StringUtil.byteToHexStringPadded(buf, value >>> 24);
        StringUtil.byteToHexStringPadded(buf, value >>> 16);
        StringUtil.byteToHexStringPadded(buf, value >>> 8);
        StringUtil.byteToHexStringPadded(buf, value);
    }

    private static void longToHexStringPadded(final StringBuilder buf, final long value) {
        intToHexStringPadded(buf, (int) (value >>> 32));
        intToHexStringPadded(buf, (int) value);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public int compareTo(final ChannelId o) {
        if (this == o) {
            // short circuit
            return 0;
        }
        if (o instanceof DefaultChannelId) {
            final int machineIdCmp = Arrays.compareUnsigned(machineId, ((DefaultChannelId) o).machineId);
            if (machineIdCmp != 0) {
                return machineIdCmp;
            }
            final int processIdCmp = Integer.compareUnsigned(processId, ((DefaultChannelId) o).processId);
            if (processIdCmp != 0) {
                return processIdCmp;
            }
            final int sequenceIdCmp = Integer.compareUnsigned(sequenceId, ((DefaultChannelId) o).sequenceId);
            if (sequenceIdCmp != 0) {
                return sequenceIdCmp;
            }
            final int mixedTimeStampIdCmp =
                    Long.compareUnsigned(mixedTimeStampId, ((DefaultChannelId) o).mixedTimeStampId);
            if (mixedTimeStampIdCmp != 0) {
                return mixedTimeStampIdCmp;
            }
            return Integer.compareUnsigned(randomId, ((DefaultChannelId) o).randomId);
        }

        return asLongText().compareTo(o.asLongText());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DefaultChannelId)) {
            return false;
        }
        final DefaultChannelId other = (DefaultChannelId) obj;
        return hashCode == other.hashCode &&
               randomId == other.randomId &&
               mixedTimeStampId == other.mixedTimeStampId &&
               sequenceId == other.sequenceId &&
               processId == other.processId &&
               Arrays.equals(machineId, other.machineId);
    }

    @Override
    public String toString() {
        return asShortText();
    }
}
