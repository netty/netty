package io.netty5.handler.codec.http.headers;

import org.junit.jupiter.api.Assumptions;

public class EmptyHttpHeadersTest extends AbstractHttpHeadersTest {
    // this basically tests that emptyHeaders() behaves like a normal header map, including validation

    @Override
    protected HttpHeaders newHeaders() {
        return HttpHeaders.emptyHeaders();
    }

    @Override
    protected HttpHeaders newHeaders(int initialSizeHint) {
        return Assumptions.abort("Empty headers have a fixed size hint");
    }
}
