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
package io.netty.channel.rxtx;

import gnu.io.SerialPort;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelConfig;
import io.netty.channel.DefaultChannelConfig;

import java.util.Map;

import static io.netty.channel.rxtx.RxtxChannelOption.*;

/**
 * A configuration class for RXTX device connections.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link ChannelConfig},
 * {@link RxtxChannelConfig} allows the following options in the option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr><tr>
 * <td>{@link io.netty.channel.rxtx.RxtxChannelOption#BAUD_RATE}</td><td>{@link #setBaudrate(int)}</td>
 * </tr><tr>
 * <td>{@link io.netty.channel.rxtx.RxtxChannelOption#DTR}</td><td>{@link #setDtr(boolean)}</td>
 * </tr><tr>
 * <td>{@link io.netty.channel.rxtx.RxtxChannelOption#RTS}</td><td>{@link #setRts(boolean)}</td>
 * </tr><tr>
 * <td>{@link io.netty.channel.rxtx.RxtxChannelOption#STOP_BITS}</td><td>{@link #setStopbits(Stopbits)}</td>
 * </tr><tr>
 * <td>{@link io.netty.channel.rxtx.RxtxChannelOption#DATA_BITS}</td><td>{@link #setDatabits(Databits)}</td>
 * </tr><tr>
 * <td>{@link io.netty.channel.rxtx.RxtxChannelOption#PARITY_BIT}</td><td>{@link #setParitybit(Paritybit)}</td>
 * </tr>
 * </table>
 */
public class RxtxChannelConfig extends DefaultChannelConfig {

    public enum Stopbits {
        /**
         * 1 stop bit will be sent at the end of every character
         */
        STOPBITS_1(SerialPort.STOPBITS_1),
        /**
         * 2 stop bits will be sent at the end of every character
         */
        STOPBITS_2(SerialPort.STOPBITS_2),
        /**
         * 1.5 stop bits will be sent at the end of every character
         */
        STOPBITS_1_5(SerialPort.STOPBITS_1_5);

        private final int value;

        Stopbits(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }

        public static Stopbits valueOf(int value) {
            for (Stopbits stopbit : Stopbits.values()) {
                if (stopbit.value == value) {
                    return stopbit;
                }
            }
            throw new IllegalArgumentException("unknown " + Stopbits.class.getSimpleName() + " value: " + value);
        }
    }

    public enum Databits {
        /**
         * 5 data bits will be used for each character (ie. Baudot code)
         */
        DATABITS_5(SerialPort.DATABITS_5),
        /**
         * 6 data bits will be used for each character
         */
        DATABITS_6(SerialPort.DATABITS_6),
        /**
         * 7 data bits will be used for each character (ie. ASCII)
         */
        DATABITS_7(SerialPort.DATABITS_7),
        /**
         * 8 data bits will be used for each character (ie. binary data)
         */
        DATABITS_8(SerialPort.DATABITS_8);

        private final int value;

        Databits(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }

        public static Databits valueOf(int value) {
            for (Databits databit : Databits.values()) {
                if (databit.value == value) {
                    return databit;
                }
            }
            throw new IllegalArgumentException("unknown " + Databits.class.getSimpleName() + " value: " + value);
        }
    }

    public enum Paritybit {
        /**
         * No parity bit will be sent with each data character at all
         */
        NONE(SerialPort.PARITY_NONE),
        /**
         * An odd parity bit will be sent with each data character, ie. will be set
         * to 1 if the data character contains an even number of bits set to 1.
         */
        ODD(SerialPort.PARITY_ODD),
        /**
         * An even parity bit will be sent with each data character, ie. will be set
         * to 1 if the data character contains an odd number of bits set to 1.
         */
        EVEN(SerialPort.PARITY_EVEN),
        /**
         * A mark parity bit (ie. always 1) will be sent with each data character
         */
        MARK(SerialPort.PARITY_MARK),
        /**
         * A space parity bit (ie. always 0) will be sent with each data character
         */
        SPACE(SerialPort.PARITY_SPACE);

        private final int value;

        Paritybit(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }

        public static Paritybit valueOf(int value) {
            for (Paritybit paritybit : Paritybit.values()) {
                if (paritybit.value == value) {
                    return paritybit;
                }
            }
            throw new IllegalArgumentException("unknown " + Paritybit.class.getSimpleName() + " value: " + value);
        }
    }

    private volatile int baudrate = 115200;
    private volatile boolean dtr;
    private volatile boolean rts;
    private volatile Stopbits stopbits = Stopbits.STOPBITS_1;
    private volatile Databits databits = Databits.DATABITS_8;
    private volatile Paritybit paritybit = Paritybit.NONE;

    public RxtxChannelConfig(RxtxChannel channel) {
        super(channel);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(), BAUD_RATE, DTR, RTS, STOP_BITS, DATA_BITS, PARITY_BIT);
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

    /**
     * Sets the baud rate (ie. bits per second) for communication with the serial device.
     * The baud rate will include bits for framing (in the form of stop bits and parity),
     * such that the effective data rate will be lower than this value.
     *
     * @param baudrate The baud rate (in bits per second)
     */
    public void setBaudrate(final int baudrate) {
        this.baudrate = baudrate;
    }

    /**
     * Sets the number of stop bits to include at the end of every character to aid the
     * serial device in synchronising with the data.
     *
     * @param stopbits The number of stop bits to use
     */
    public void setStopbits(final Stopbits stopbits) {
        this.stopbits = stopbits;
    }

    /**
     * Sets the number of data bits to use to make up each character sent to the serial
     * device.
     *
     * @param databits The number of data bits to use
     */
    public void setDatabits(final Databits databits) {
        this.databits = databits;
    }

    /**
     * Sets the type of parity bit to be used when communicating with the serial device.
     *
     * @param paritybit The type of parity bit to be used
     */
    public void setParitybit(final Paritybit paritybit) {
        this.paritybit = paritybit;
    }

    /**
     * @return The configured baud rate, defaulting to 115200 if unset
     */
    public int getBaudrate() {
        return baudrate;
    }

    /**
     * @return The configured stop bits, defaulting to {@link Stopbits#STOPBITS_1} if unset
     */
    public Stopbits getStopbits() {
        return stopbits;
    }

    /**
     * @return The configured data bits, defaulting to {@link Databits#DATABITS_8} if unset
     */
    public Databits getDatabits() {
        return databits;
    }

    /**
     * @return The configured parity bit, defaulting to {@link Paritybit#NONE} if unset
     */
    public Paritybit getParitybit() {
        return paritybit;
    }

    /**
     * @return true if the serial device should support the Data Terminal Ready signal
     */
    public boolean isDtr() {
        return dtr;
    }

    /**
     * Sets whether the serial device supports the Data Terminal Ready signal, used for
     * flow control
     *
     * @param dtr true if DTR is supported, false otherwise
     */
    public void setDtr(final boolean dtr) {
        this.dtr = dtr;
    }

    /**
     * @return true if the serial device should support the Ready to Send signal
     */
    public boolean isRts() {
        return rts;
    }

    /**
     * Sets whether the serial device supports the Request To Send signal, used for flow
     * control
     *
     * @param rts true if RTS is supported, false otherwise
     */
    public void setRts(final boolean rts) {
        this.rts = rts;
    }
}
