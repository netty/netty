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
package io.netty.handler.codec.http;


final class CookieEncoderUtil {

    static String stripTrailingSeparator(StringBuilder buf) {
        if (buf.length() > 0) {
            buf.setLength(buf.length() - 2);
        }
        return buf.toString();
    }

    static int estimateClientLength(Cookie c) {
        int estimate = estimateLength(c.getName(), c.getValue());

        if (c.getPath() != null) {
            estimate += estimateLength(CookieHeaderNames.PATH, c.getPath());
        }

        if (c.getDomain() != null) {
            estimate += estimateLength(CookieHeaderNames.DOMAIN, c.getDomain());
        }

        if (c.getVersion() > 0) {
            estimate += 12; // '$Version=N; '
            int numPorts = c.getPorts().size();
            if (numPorts > 0) {
                estimate += 10; // '$Port=""; '
                estimate += numPorts * 4;
            }
        }

        return estimate;
    }

    static int estimateServerLength(Cookie c) {
        int estimate = estimateLength(c.getName(), c.getValue());

        if (c.getMaxAge() != Long.MIN_VALUE) {
            estimate += 41; // 'Expires=Sun, 06 Nov 1994 08:49:37 +0900; '
        }

        if (c.getPath() != null) {
            estimate += estimateLength(CookieHeaderNames.PATH, c.getPath());
        }

        if (c.getDomain() != null) {
            estimate += estimateLength(CookieHeaderNames.DOMAIN, c.getDomain());
        }
        if (c.isSecure()) {
            estimate += 8; // 'Secure; '
        }
        if (c.isHttpOnly()) {
            estimate += 10; // 'HTTPOnly; '
        }
        if (c.getVersion() > 0) {
            estimate += 11; // 'Version=N; '
            if (c.getComment() != null) {
                estimate += estimateLength(CookieHeaderNames.COMMENT, c.getComment());
            }

            if (c.getCommentUrl() != null) {
                estimate += estimateLength(CookieHeaderNames.COMMENTURL, c.getCommentUrl());
            }

            int numPorts = c.getPorts().size();
            if (numPorts > 0) {
                estimate += 9; // 'Port=""; '
                estimate += numPorts * 4;
            }

            if (c.isDiscard()) {
                estimate += 9; // 'Discard; '
            }
        }

        return estimate;
    }

    private static int estimateLength(String name, String value) {
        return name.length() + value.length() + 6;
    }

    static void add(StringBuilder sb, String name, String val) {
        if (val == null) {
            addQuoted(sb, name, "");
            return;
        }

        for (int i = 0; i < val.length(); i ++) {
            char c = val.charAt(i);
            switch (c) {
            case '\t': case ' ': case '"': case '(':  case ')': case ',':
            case '/':  case ':': case ';': case '<':  case '=': case '>':
            case '?':  case '@': case '[': case '\\': case ']':
            case '{':  case '}':
                addQuoted(sb, name, val);
                return;
            }
        }

        addUnquoted(sb, name, val);
    }

    static void addUnquoted(StringBuilder sb, String name, String val) {
        sb.append(name);
        sb.append((char) HttpConstants.EQUALS);
        sb.append(val);
        sb.append((char) HttpConstants.SEMICOLON);
        sb.append((char) HttpConstants.SP);
    }

    static void addQuoted(StringBuilder sb, String name, String val) {
        if (val == null) {
            val = "";
        }

        sb.append(name);
        sb.append((char) HttpConstants.EQUALS);
        sb.append((char) HttpConstants.DOUBLE_QUOTE);
        sb.append(val.replace("\\", "\\\\").replace("\"", "\\\""));
        sb.append((char) HttpConstants.DOUBLE_QUOTE);
        sb.append((char) HttpConstants.SEMICOLON);
        sb.append((char) HttpConstants.SP);
    }

    static void add(StringBuilder sb, String name, long val) {
        sb.append(name);
        sb.append((char) HttpConstants.EQUALS);
        sb.append(val);
        sb.append((char) HttpConstants.SEMICOLON);
        sb.append((char) HttpConstants.SP);
    }

    private CookieEncoderUtil() {
        // Unused
    }
}
