package io.netty.handler.codec.http;

import static io.netty.handler.codec.http.CookieEncoderUtil.*;

/**
 * Encodes client-side {@link Cookie}s into an HTTP header value.  This encoder can encode
 * the HTTP cookie version 0, 1, and 2.
 * <pre>
 * // Example
 * {@link HttpRequest} req = ...;
 * res.setHeader("Cookie", {@link ClientCookieEncoder}.encode("JSESSIONID", "1234"));
 * </pre>
 *
 * @see CookieDecoder
 *
 * @apiviz.stereotype utility
 * @apiviz.has        io.netty.handler.codec.http.Cookie oneway - - encodes
 */
public final class ClientCookieEncoder {

    /**
     * Encodes the specified cookie into an HTTP header value.
     */
    public static String encode(String name, String value) {
        return encode(new DefaultCookie(name, value));
    }

    public static String encode(Cookie cookie) {
        if (cookie == null) {
            throw new NullPointerException("cookie");
        }

        StringBuilder buf = new StringBuilder();
        encode(buf, cookie);
        return stripTrailingSeparator(buf);
    }

    public static String encode(Cookie... cookies) {
        if (cookies == null) {
            throw new NullPointerException("cookies");
        }

        StringBuilder buf = new StringBuilder();
        for (Cookie c: cookies) {
            if (c == null) {
                break;
            }

            encode(buf, c);
        }
        return stripTrailingSeparator(buf);
    }

    public static String encode(Iterable<Cookie> cookies) {
        if (cookies == null) {
            throw new NullPointerException("cookies");
        }

        StringBuilder buf = new StringBuilder();
        for (Cookie c: cookies) {
            if (c == null) {
                break;
            }

            encode(buf, c);
        }
        return stripTrailingSeparator(buf);
    }

    private static void encode(StringBuilder buf, Cookie c) {
        if (c.getVersion() >= 1) {
            add(buf, '$' + CookieHeaderNames.VERSION, 1);
        }

        add(buf, c.getName(), c.getValue());

        if (c.getPath() != null) {
            add(buf, '$' + CookieHeaderNames.PATH, c.getPath());
        }

        if (c.getDomain() != null) {
            add(buf, '$' + CookieHeaderNames.DOMAIN, c.getDomain());
        }

        if (c.getVersion() >= 1) {
            if (!c.getPorts().isEmpty()) {
                buf.append('$');
                buf.append(CookieHeaderNames.PORT);
                buf.append((char) HttpConstants.EQUALS);
                buf.append((char) HttpConstants.DOUBLE_QUOTE);
                for (int port: c.getPorts()) {
                    buf.append(port);
                    buf.append((char) HttpConstants.COMMA);
                }
                buf.setCharAt(buf.length() - 1, (char) HttpConstants.DOUBLE_QUOTE);
                buf.append((char) HttpConstants.SEMICOLON);
            }
        }
    }

    private ClientCookieEncoder() {
        // Unused
    }
}
