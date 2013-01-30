package io.netty.handler.codec.http.multipart;

import org.junit.Test;

import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;

/**
 * Pablo Fernandez's Java OAuth library (https://github.com/fernandezpablo85/scribe-java)
 * is one of the most accurate and compliant.
 * The {@link HttpPostRequestEncoder#encodeAttribute(String, java.nio.charset.Charset)}'s tests
 * are a port of his originals.
 */
public class HttpPostRequestEncoderTest {
    private static final Charset utf8 = Charset.forName("UTF-8");

    @Test
    public void shouldPercentEncodeString() throws HttpPostRequestEncoder.ErrorDataEncoderException {
        String plain = "this is a test &^";
        String encoded = "this%20is%20a%20test%20%26%5E";
        assertEquals(encoded, HttpPostRequestEncoder.encodeAttribute(plain, utf8));
    }

    @Test
    public void shouldPercentEncodeAllSpecialCharacters() throws HttpPostRequestEncoder.ErrorDataEncoderException {
        String plain = "!*'();:@&=+$,/?#[]";
        String encoded = "%21%2A%27%28%29%3B%3A%40%26%3D%2B%24%2C%2F%3F%23%5B%5D";
        assertEquals(encoded, HttpPostRequestEncoder.encodeAttribute(plain, utf8));
    }

    @Test
    public void shouldNotPercentEncodeReservedCharacters() throws HttpPostRequestEncoder.ErrorDataEncoderException {
        String plain = "abcde123456-._~";
        String encoded = plain;
        assertEquals(encoded, HttpPostRequestEncoder.encodeAttribute(plain, utf8));
    }

    @Test
    public void returnEmptyStringIfNull() throws HttpPostRequestEncoder.ErrorDataEncoderException {
        String toEncode = null;
        assertEquals("", HttpPostRequestEncoder.encodeAttribute(toEncode, utf8));
    }

    @Test
    public void shouldPercentEncodeTwitterExamples() throws HttpPostRequestEncoder.ErrorDataEncoderException {
        // These tests are part of the Twitter dev examples here -> https://dev.twitter.com/docs/auth/percent-encoding-parameters
        String sources[] = { "Ladies + Gentlemen", "An encoded string!", "Dogs, Cats & Mice" };
        String encoded[] = { "Ladies%20%2B%20Gentlemen", "An%20encoded%20string%21", "Dogs%2C%20Cats%20%26%20Mice" };

        for (int i = 0; i < sources.length; i++) {
            assertEquals(encoded[i], HttpPostRequestEncoder.encodeAttribute(sources[i], utf8));
        }
    }
}
