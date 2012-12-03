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

package org.jboss.netty.handler.ipfilter;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.internal.StringUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * The Class PatternRule represents an IP filter rule using string patterns.
 * <br>
 * Rule Syntax:
 * <br>
 * <pre>
 * Rule ::= [n|i]:address          n stands for computer name, i for ip address
 * address ::= &lt;regex&gt; | localhost
 * regex is a regular expression with '*' as multi character and '?' as single character wild card
 * </pre>
 * <br>
 * Example: allow localhost:
 * <br>
 * new PatternRule(true, "n:localhost")
 * <br>
 * Example: allow local lan:
 * <br>
 * new PatternRule(true, "i:192.168.0.*")
 * <br>
 * Example: block all
 * <br>
 * new PatternRule(false, "n:*")
 * <br>
 */
public class PatternRule implements IpFilterRule, Comparable<Object> {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PatternRule.class);

    private Pattern ipPattern;

    private Pattern namePattern;

    private boolean isAllowRule = true;

    private boolean localhost;

    private final String pattern;

    /**
     * Instantiates a new pattern rule.
     *
     * @param allow   indicates if this is an allow or block rule
     * @param pattern the filter pattern
     */
    public PatternRule(boolean allow, String pattern) {
        isAllowRule = allow;
        this.pattern = pattern;
        parse(pattern);
    }

    /**
     * returns the pattern.
     *
     * @return the pattern
     */
    public String getPattern() {
        return pattern;
    }

    public boolean isAllowRule() {
        return isAllowRule;
    }

    public boolean isDenyRule() {
        return !isAllowRule;
    }

    public boolean contains(InetAddress inetAddress) {
        if (localhost) {
            if (isLocalhost(inetAddress)) {
                return true;
            }
        }
        if (ipPattern != null) {
            if (ipPattern.matcher(inetAddress.getHostAddress()).matches()) {
                return true;
            }
        }
        if (namePattern != null) {
            if (namePattern.matcher(inetAddress.getHostName()).matches()) {
                return true;
            }
        }
        return false;
    }

    private void parse(String pattern) {
        if (pattern == null) {
            return;
        }

        String[] acls = StringUtil.split(pattern, ',');

        String ip = "";
        String name = "";
        for (String c : acls) {
            c = c.trim();
            if ("n:localhost".equals(c)) {
                localhost = true;
            } else if (c.startsWith("n:")) {
                name = addRule(name, c.substring(2));
            } else if (c.startsWith("i:")) {
                ip = addRule(ip, c.substring(2));
            }
        }
        if (ip.length() != 0) {
            ipPattern = Pattern.compile(ip);
        }
        if (name.length() != 0) {
            namePattern = Pattern.compile(name);
        }
    }

    private static String addRule(String pattern, String rule) {
        if (rule == null || rule.length() == 0) {
            return pattern;
        }
        if (pattern.length() != 0) {
            pattern += "|";
        }
        rule = rule.replaceAll("\\.", "\\\\.");
        rule = rule.replaceAll("\\*", ".*");
        rule = rule.replaceAll("\\?", ".");
        pattern += '(' + rule + ')';
        return pattern;
    }

    private static boolean isLocalhost(InetAddress address) {
        try {
            if (address.equals(InetAddress.getLocalHost())) {
                return true;
            }
        } catch (UnknownHostException e) {
            if (logger.isInfoEnabled()) {
                logger.info("error getting ip of localhost", e);
            }
        }
        try {
            InetAddress[] addrs = InetAddress.getAllByName("127.0.0.1");
            for (InetAddress addr : addrs) {
                if (addr.equals(address)) {
                    return true;
                }
            }
        } catch (UnknownHostException e) {
            if (logger.isInfoEnabled()) {
                logger.info("error getting ip of localhost", e);
            }
        }
        return false;
    }

    public int compareTo(Object o) {
        if (o == null) {
            return -1;
        }
        if (!(o instanceof PatternRule)) {
            return -1;
        }
        PatternRule p = (PatternRule) o;
        if (p.isAllowRule() && !isAllowRule) {
            return -1;
        }
        if (pattern == null && p.pattern == null) {
            return 0;
        }
        if (pattern != null) {
            return pattern.compareTo(p.getPattern());
        }
        return -1;
    }

}
