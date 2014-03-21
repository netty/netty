/*
 * Copyright 2012 The Netty Project
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
package io.netty.util;

import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.StringTokenizer;

/**
 * A class that holds a number of network-related constants.
 * <p/>
 * This class borrowed some of its methods from a  modified fork of the
 * <a href="http://svn.apache.org/repos/asf/harmony/enhanced/java/branches/java6/classlib/modules/luni/
 * src/main/java/org/apache/harmony/luni/util/Inet6Util.java">Inet6Util class</a> which was part of Apache Harmony.
 */
public final class NetUtil {

    /**
     * The {@link Inet4Address} that represents the IPv4 loopback address '127.0.0.1'
     */
    public static final Inet4Address LOCALHOST4;

    /**
     * The {@link Inet6Address} that represents the IPv6 loopback address '::1'
     */
    public static final Inet6Address LOCALHOST6;

    /**
     * The {@link InetAddress} that represents the loopback address. If IPv6 stack is available, it will refer to
     * {@link #LOCALHOST6}.  Otherwise, {@link #LOCALHOST4}.
     */
    public static final InetAddress LOCALHOST;

    /**
     * The loopback {@link NetworkInterface} of the current machine
     */
    public static final NetworkInterface LOOPBACK_IF;

    /**
     * The SOMAXCONN value of the current machine.  If failed to get the value, 3072 is used as a
     * default value.
     */
    public static final int SOMAXCONN;

    /**
     * The logger being used by this class
     */
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NetUtil.class);

    static {
        byte[] LOCALHOST4_BYTES = {127, 0, 0, 1};
        byte[] LOCALHOST6_BYTES = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};

        // Create IPv4 loopback address.
        Inet4Address localhost4 = null;
        try {
            localhost4 = (Inet4Address) InetAddress.getByAddress(LOCALHOST4_BYTES);
        } catch (Exception e) {
            // We should not get here as long as the length of the address is correct.
            PlatformDependent.throwException(e);
        }
        LOCALHOST4 = localhost4;

        // Create IPv6 loopback address.
        Inet6Address localhost6 = null;
        try {
            localhost6 = (Inet6Address) InetAddress.getByAddress(LOCALHOST6_BYTES);
        } catch (Exception e) {
            // We should not get here as long as the length of the address is correct.
            PlatformDependent.throwException(e);
        }
        LOCALHOST6 = localhost6;

        // Retrieve the list of available network interfaces.
        List<NetworkInterface> ifaces = new ArrayList<NetworkInterface>();
        try {
            for (Enumeration<NetworkInterface> i = NetworkInterface.getNetworkInterfaces(); i.hasMoreElements();) {
                NetworkInterface iface = i.nextElement();
                // Use the interface with proper INET addresses only.
                if (iface.getInetAddresses().hasMoreElements()) {
                    ifaces.add(iface);
                }
            }
        } catch (SocketException e) {
            logger.warn("Failed to retrieve the list of available network interfaces", e);
        }

        // Find the first loopback interface available from its INET address (127.0.0.1 or ::1)
        // Note that we do not use NetworkInterface.isLoopback() in the first place because it takes long time
        // on a certain environment. (e.g. Windows with -Djava.net.preferIPv4Stack=true)
        NetworkInterface loopbackIface = null;
        InetAddress loopbackAddr = null;
        loop: for (NetworkInterface iface: ifaces) {
            for (Enumeration<InetAddress> i = iface.getInetAddresses(); i.hasMoreElements();) {
                InetAddress addr = i.nextElement();
                if (addr.isLoopbackAddress()) {
                    // Found
                    loopbackIface = iface;
                    loopbackAddr = addr;
                    break loop;
                }
            }
        }

        // If failed to find the loopback interface from its INET address, fall back to isLoopback().
        if (loopbackIface == null) {
            try {
                for (NetworkInterface iface: ifaces) {
                    if (iface.isLoopback()) {
                        Enumeration<InetAddress> i = iface.getInetAddresses();
                        if (i.hasMoreElements()) {
                            // Found the one with INET address.
                            loopbackIface = iface;
                            loopbackAddr = i.nextElement();
                            break;
                        }
                    }
                }

                if (loopbackIface == null) {
                    logger.warn("Failed to find the loopback interface");
                }
            } catch (SocketException e) {
                logger.warn("Failed to find the loopback interface", e);
            }
        }

        if (loopbackIface != null) {
            // Found the loopback interface with an INET address.
            logger.debug(
                    "Loopback interface: {} ({}, {})",
                    loopbackIface.getName(), loopbackIface.getDisplayName(), loopbackAddr.getHostAddress());
        } else {
            // Could not find the loopback interface, but we can't leave LOCALHOST as null.
            // Use LOCALHOST6 or LOCALHOST4, preferably the IPv6 one.
            if (loopbackAddr == null) {
                try {
                    if (NetworkInterface.getByInetAddress(LOCALHOST6) != null) {
                        logger.debug("Using hard-coded IPv6 localhost address: {}", localhost6);
                        loopbackAddr = localhost6;
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    if (loopbackAddr == null) {
                        logger.debug("Using hard-coded IPv4 localhost address: {}", localhost4);
                        loopbackAddr = localhost4;
                    }
                }
            }
        }

        LOOPBACK_IF = loopbackIface;
        LOCALHOST = loopbackAddr;

        // Determine the default somaxconn (server socket backlog) value of the platform.
        int somaxconn = 3072;
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader("/proc/sys/net/core/somaxconn"));
            somaxconn = Integer.parseInt(in.readLine());
            logger.debug("/proc/sys/net/core/somaxconn: {}", somaxconn);
        } catch (Exception e) {
            // Failed to get SOMAXCONN
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                    // Ignored.
                }
            }
        }

        SOMAXCONN = somaxconn;
    }

    /**
     * Creates an byte[] based on an ipAddressString. No error handling is
     * performed here.
     */
    public static byte[] createByteArrayFromIpAddressString(String ipAddressString) {

        if (isValidIpV4Address(ipAddressString)) {
            StringTokenizer tokenizer = new StringTokenizer(ipAddressString, ".");
            String token;
            int tempInt;
            byte[] byteAddress = new byte[4];
            for (int i = 0; i < 4; i ++) {
                token = tokenizer.nextToken();
                tempInt = Integer.parseInt(token);
                byteAddress[i] = (byte) tempInt;
            }

            return byteAddress;
        }

        if (isValidIpV6Address(ipAddressString)) {
            if (ipAddressString.charAt(0) == '[') {
                ipAddressString = ipAddressString.substring(1, ipAddressString.length() - 1);
            }

            StringTokenizer tokenizer = new StringTokenizer(ipAddressString, ":.", true);
            ArrayList<String> hexStrings = new ArrayList<String>();
            ArrayList<String> decStrings = new ArrayList<String>();
            String token = "";
            String prevToken = "";
            int doubleColonIndex = -1; // If a double colon exists, we need to
            // insert 0s.

            // Go through the tokens, including the seperators ':' and '.'
            // When we hit a : or . the previous token will be added to either
            // the hex list or decimal list. In the case where we hit a ::
            // we will save the index of the hexStrings so we can add zeros
            // in to fill out the string
            while (tokenizer.hasMoreTokens()) {
                prevToken = token;
                token = tokenizer.nextToken();

                if (":".equals(token)) {
                    if (":".equals(prevToken)) {
                        doubleColonIndex = hexStrings.size();
                    } else if (!prevToken.isEmpty()) {
                        hexStrings.add(prevToken);
                    }
                } else if (".".equals(token)) {
                    decStrings.add(prevToken);
                }
            }

            if (":".equals(prevToken)) {
                if (":".equals(token)) {
                    doubleColonIndex = hexStrings.size();
                } else {
                    hexStrings.add(token);
                }
            } else if (".".equals(prevToken)) {
                decStrings.add(token);
            }

            // figure out how many hexStrings we should have
            // also check if it is a IPv4 address
            int hexStringsLength = 8;

            // If we have an IPv4 address tagged on at the end, subtract
            // 4 bytes, or 2 hex words from the total
            if (!decStrings.isEmpty()) {
                hexStringsLength -= 2;
            }

            // if we hit a double Colon add the appropriate hex strings
            if (doubleColonIndex != -1) {
                int numberToInsert = hexStringsLength - hexStrings.size();
                for (int i = 0; i < numberToInsert; i ++) {
                    hexStrings.add(doubleColonIndex, "0");
                }
            }

            byte[] ipByteArray = new byte[16];

            // Finally convert these strings to bytes...
            for (int i = 0; i < hexStrings.size(); i ++) {
                convertToBytes(hexStrings.get(i), ipByteArray, i * 2);
            }

            // Now if there are any decimal values, we know where they go...
            for (int i = 0; i < decStrings.size(); i ++) {
                ipByteArray[i + 12] = (byte) (Integer.parseInt(decStrings.get(i)) & 255);
            }
            return ipByteArray;
        }
        return null;
    }

    /**
     * Converts a 4 character hex word into a 2 byte word equivalent
     */
    private static void convertToBytes(String hexWord, byte[] ipByteArray, int byteIndex) {

        int hexWordLength = hexWord.length();
        int hexWordIndex = 0;
        ipByteArray[byteIndex] = 0;
        ipByteArray[byteIndex + 1] = 0;
        int charValue;

        // high order 4 bits of first byte
        if (hexWordLength > 3) {
            charValue = getIntValue(hexWord.charAt(hexWordIndex ++));
            ipByteArray[byteIndex] |= charValue << 4;
        }

        // low order 4 bits of the first byte
        if (hexWordLength > 2) {
            charValue = getIntValue(hexWord.charAt(hexWordIndex ++));
            ipByteArray[byteIndex] |= charValue;
        }

        // high order 4 bits of second byte
        if (hexWordLength > 1) {
            charValue = getIntValue(hexWord.charAt(hexWordIndex ++));
            ipByteArray[byteIndex + 1] |= charValue << 4;
        }

        // low order 4 bits of the first byte
        charValue = getIntValue(hexWord.charAt(hexWordIndex));
        ipByteArray[byteIndex + 1] |= charValue & 15;
    }

    static int getIntValue(char c) {

        switch (c) {
            case '0':
                return 0;
            case '1':
                return 1;
            case '2':
                return 2;
            case '3':
                return 3;
            case '4':
                return 4;
            case '5':
                return 5;
            case '6':
                return 6;
            case '7':
                return 7;
            case '8':
                return 8;
            case '9':
                return 9;
        }

        c = Character.toLowerCase(c);
        switch (c) {
            case 'a':
                return 10;
            case 'b':
                return 11;
            case 'c':
                return 12;
            case 'd':
                return 13;
            case 'e':
                return 14;
            case 'f':
                return 15;
        }
        return 0;
    }

    public static boolean isValidIpV6Address(String ipAddress) {
        int length = ipAddress.length();
        boolean doubleColon = false;
        int numberOfColons = 0;
        int numberOfPeriods = 0;
        int numberOfPercent = 0;
        StringBuilder word = new StringBuilder();
        char c = 0;
        char prevChar;
        int offset = 0; // offset for [] ip addresses

        if (length < 2) {
            return false;
        }

        for (int i = 0; i < length; i ++) {
            prevChar = c;
            c = ipAddress.charAt(i);
            switch (c) {

                // case for an open bracket [x:x:x:...x]
                case '[':
                    if (i != 0) {
                        return false; // must be first character
                    }
                    if (ipAddress.charAt(length - 1) != ']') {
                        return false; // must have a close ]
                    }
                    offset = 1;
                    if (length < 4) {
                        return false;
                    }
                    break;

                // case for a closed bracket at end of IP [x:x:x:...x]
                case ']':
                    if (i != length - 1) {
                        return false; // must be last charcter
                    }
                    if (ipAddress.charAt(0) != '[') {
                        return false; // must have a open [
                    }
                    break;

                // case for the last 32-bits represented as IPv4 x:x:x:x:x:x:d.d.d.d
                case '.':
                    numberOfPeriods ++;
                    if (numberOfPeriods > 3) {
                        return false;
                    }
                    if (!isValidIp4Word(word.toString())) {
                        return false;
                    }
                    if (numberOfColons != 6 && !doubleColon) {
                        return false;
                    }
                    // a special case ::1:2:3:4:5:d.d.d.d allows 7 colons with an
                    // IPv4 ending, otherwise 7 :'s is bad
                    if (numberOfColons == 7 && ipAddress.charAt(offset) != ':' &&
                        ipAddress.charAt(1 + offset) != ':') {
                        return false;
                    }
                    word.delete(0, word.length());
                    break;

                case ':':
                    // FIX "IP6 mechanism syntax #ip6-bad1"
                    // An IPV6 address cannot start with a single ":".
                    // Either it can starti with "::" or with a number.
                    if (i == offset && (ipAddress.length() <= i || ipAddress.charAt(i + 1) != ':')) {
                        return false;
                    }
                    // END FIX "IP6 mechanism syntax #ip6-bad1"
                    numberOfColons ++;
                    if (numberOfColons > 7) {
                        return false;
                    }
                    if (numberOfPeriods > 0) {
                        return false;
                    }
                    if (prevChar == ':') {
                        if (doubleColon) {
                            return false;
                        }
                        doubleColon = true;
                    }
                    word.delete(0, word.length());
                    break;
                case '%':
                    if (numberOfColons == 0) {
                        return false;
                    }
                    numberOfPercent ++;

                    // validate that the stuff after the % is valid
                    if (i + 1 >= length) {
                        // in this case the percent is there but no number is
                        // available
                        return false;
                    }
                    try {
                        if (Integer.parseInt(ipAddress.substring(i + 1)) < 0) {
                            return false;
                        }
                    } catch (NumberFormatException e) {
                        // right now we just support an integer after the % so if
                        // this is not
                        // what is there then return
                        return false;
                    }
                    break;

                default:
                    if (numberOfPercent == 0) {
                        if (word != null && word.length() > 3) {
                            return false;
                        }
                        if (!isValidHexChar(c)) {
                            return false;
                        }
                    }
                    word.append(c);
            }
        }

        // Check if we have an IPv4 ending
        if (numberOfPeriods > 0) {
            // There is a test case with 7 colons and valid ipv4 this should resolve it
            if (numberOfPeriods != 3 || !(isValidIp4Word(word.toString()) && numberOfColons < 7)) {
                return false;
            }
        } else {
            // If we're at then end and we haven't had 7 colons then there is a
            // problem unless we encountered a doubleColon
            if (numberOfColons != 7 && !doubleColon) {
                return false;
            }

            // If we have an empty word at the end, it means we ended in either
            // a : or a .
            // If we did not end in :: then this is invalid
            if (numberOfPercent == 0) {
                if (word.length() == 0 && ipAddress.charAt(length - 1 - offset) == ':' &&
                    ipAddress.charAt(length - 2 - offset) != ':') {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean isValidIp4Word(String word) {
        char c;
        if (word.length() < 1 || word.length() > 3) {
            return false;
        }
        for (int i = 0; i < word.length(); i ++) {
            c = word.charAt(i);
            if (!(c >= '0' && c <= '9')) {
                return false;
            }
        }
        return Integer.parseInt(word) <= 255;
    }

    static boolean isValidHexChar(char c) {
        return c >= '0' && c <= '9' || c >= 'A' && c <= 'F' || c >= 'a' && c <= 'f';
    }

    /**
     * Takes a string and parses it to see if it is a valid IPV4 address.
     *
     * @return true, if the string represents an IPV4 address in dotted
     *         notation, false otherwise
     */
    public static boolean isValidIpV4Address(String value) {

        int periods = 0;
        int i;
        int length = value.length();

        if (length > 15) {
            return false;
        }
        char c;
        StringBuilder word = new StringBuilder();
        for (i = 0; i < length; i ++) {
            c = value.charAt(i);
            if (c == '.') {
                periods ++;
                if (periods > 3) {
                    return false;
                }
                if (word.length() == 0) {
                    return false;
                }
                if (Integer.parseInt(word.toString()) > 255) {
                    return false;
                }
                word.delete(0, word.length());
            } else if (!Character.isDigit(c)) {
                return false;
            } else {
                if (word.length() > 2) {
                    return false;
                }
                word.append(c);
            }
        }

        if (word.length() == 0 || Integer.parseInt(word.toString()) > 255) {
            return false;
        }

        return periods == 3;
    }

    /**
     * A constructor to stop this class being constructed.
     */
    private NetUtil() {
        // Unused
    }
}
