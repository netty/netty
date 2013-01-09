/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.transport.rxtx;

import static io.netty.transport.rxtx.RxtxChannelOptions.*;

import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;

import java.util.Map;

import gnu.io.SerialPort;

/**
 * A configuration class for RXTX device connections.
 */
public class RxtxChannelConfig extends DefaultChannelConfig {

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

    private boolean dtr;

    private boolean rts;

    private Stopbits stopbits = Stopbits.STOPBITS_1;

    private Databits databits = Databits.DATABITS_8;

    private Paritybit paritybit = Paritybit.NONE;

    public RxtxChannelConfig(RxtxChannel channel) {
        super(channel);
    }
    
    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(),
                          BAUD_RATE, DTR, RTS, STOP_BITS, DATA_BITS, PARITY_BIT);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == BAUD_RATE) {
            return (T) Integer.valueOf(getBaudrate());
        }
        if (option == DTR) {
            return (T) Boolean.valueOf(isDtr());
        }
        if (option == RTS) {
            return (T) Boolean.valueOf(isRts());
        }
        if (option == STOP_BITS) {
            return (T) getStopbits();
        }
        if (option == DATA_BITS) {
            return (T) getDatabits();
        }
        if (option == PARITY_BIT) {
            return (T) getParitybit();
        }
        
        return super.getOption(option);
    }
    
    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);
        
        if (option == BAUD_RATE) {
            setBaudrate((Integer) value);
        } else if (option == DTR) {
            setDtr((Boolean) value);
        } else if (option == RTS) {
            setRts((Boolean) value);
        } else if (option == STOP_BITS) {
            setStopbits((Stopbits) value);
        } else if (option == DATA_BITS) {
            setDatabits((Databits) value);
        } else if (option == PARITY_BIT) {
            setParitybit((Paritybit) value);
        } else {
            return super.setOption(option, value);
        }
        
        return true;
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

    public void setParitybit(final Paritybit paritybit) {
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
