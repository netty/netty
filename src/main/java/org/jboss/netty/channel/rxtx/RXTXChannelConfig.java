/*
 * Copyright 2011 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.rxtx;

import gnu.io.SerialPort;

import org.jboss.netty.channel.DefaultChannelConfig;
import org.jboss.netty.util.internal.ConversionUtil;

/**
 * A configuration class for RXTX device connections.
 *
 * @author Daniel Bimschas
 * @author Dennis Pfisterer
 */
public class RXTXChannelConfig extends DefaultChannelConfig {

    public enum Stopbits {

        STOPBITS_1(SerialPort.STOPBITS_1),
        STOPBITS_2(SerialPort.STOPBITS_2),
        STOPBITS_1_5(SerialPort.STOPBITS_1_5);

        private final int value;

        Stopbits(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static Stopbits ofValue(int value) {
            for (Stopbits stopbit : Stopbits.values()) {
                if (stopbit.value == value) {
                    return stopbit;
                }
            }
            throw new IllegalArgumentException("Unknown value for Stopbits: " + value + ".");
        }
    }

    public enum Databits {

        DATABITS_5(SerialPort.DATABITS_5),
        DATABITS_6(SerialPort.DATABITS_6),
        DATABITS_7(SerialPort.DATABITS_7),
        DATABITS_8(SerialPort.DATABITS_8);

        private final int value;

        Databits(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static Databits ofValue(int value) {
            for (Databits databit : Databits.values()) {
                if (databit.value == value) {
                    return databit;
                }
            }
            throw new IllegalArgumentException("Unknown value for Databits: " + value + ".");
        }
    }

    public enum Paritybit {

        NONE(SerialPort.PARITY_NONE),
        ODD(SerialPort.PARITY_ODD),
        EVEN(SerialPort.PARITY_EVEN),
        MARK(SerialPort.PARITY_MARK),
        SPACE(SerialPort.PARITY_SPACE);

        private final int value;

        Paritybit(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static Paritybit ofValue(int value) {
            for (Paritybit paritybit : Paritybit.values()) {
                if (paritybit.value == value) {
                    return paritybit;
                }
            }
            throw new IllegalArgumentException("Unknown value for paritybit: " + value + ".");
        }
    }

    private int baudrate = 115200;

    private boolean dtr = false;

    private boolean rts = false;

    private Stopbits stopbits = RXTXChannelConfig.Stopbits.STOPBITS_1;

    private Databits databits = RXTXChannelConfig.Databits.DATABITS_8;

    private Paritybit paritybit = RXTXChannelConfig.Paritybit.NONE;

    public RXTXChannelConfig() {
        // work with defaults ...
    }

    public RXTXChannelConfig(final int baudrate, final boolean dtr, final boolean rts, final Stopbits stopbits,
                             final Databits databits, final Paritybit paritybit) {
        this.baudrate = baudrate;
        this.dtr = dtr;
        this.rts = rts;
        this.stopbits = stopbits;
        this.databits = databits;
        this.paritybit = paritybit;
    }

    @Override
    public boolean setOption(final String key, final Object value) {
        if (key.equals("baudrate")) {
            setBaudrate(ConversionUtil.toInt(value));
            return true;
        } else if (key.equals("stopbits")) {
            setStopbits((Stopbits) value);
            return true;
        } else if (key.equals("databits")) {
            setDatabits((Databits) value);
            return true;
        } else if (key.equals("paritybit")) {
            setParitybit((Paritybit) value);
            return true;
        } else {
            return super.setOption(key, value);
        }
    }

    public void setBaudrate(final int baudrate) {
        this.baudrate = baudrate;
    }

    public void setStopbits(final Stopbits stopbits) {
        this.stopbits = stopbits;
    }

    public void setDatabits(final Databits databits) {
        this.databits = databits;
    }

    private void setParitybit(final Paritybit paritybit) {
        this.paritybit = paritybit;
    }

    public int getBaudrate() {
        return baudrate;
    }

    public Stopbits getStopbits() {
        return stopbits;
    }

    public Databits getDatabits() {
        return databits;
    }

    public Paritybit getParitybit() {
        return paritybit;
    }

    public boolean isDtr() {
        return dtr;
    }

    public void setDtr(final boolean dtr) {
        this.dtr = dtr;
    }

    public boolean isRts() {
        return rts;
    }

    public void setRts(final boolean rts) {
        this.rts = rts;
    }
}
