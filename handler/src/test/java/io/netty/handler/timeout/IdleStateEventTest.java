package io.netty.handler.timeout;

import org.junit.Test;

import static io.netty.handler.timeout.IdleStateEvent.*;
import static org.hamcrest.Matchers.hasToString;
import static org.junit.Assert.*;

public class IdleStateEventTest {
    @Test
    public void testHumanReadableToString() {
        assertThat(FIRST_READER_IDLE_STATE_EVENT, hasToString("IdleStateEvent(READER_IDLE, first)"));
        assertThat(READER_IDLE_STATE_EVENT, hasToString("IdleStateEvent(READER_IDLE)"));
        assertThat(FIRST_WRITER_IDLE_STATE_EVENT, hasToString("IdleStateEvent(WRITER_IDLE, first)"));
        assertThat(WRITER_IDLE_STATE_EVENT, hasToString("IdleStateEvent(WRITER_IDLE)"));
        assertThat(FIRST_ALL_IDLE_STATE_EVENT, hasToString("IdleStateEvent(ALL_IDLE, first)"));
        assertThat(ALL_IDLE_STATE_EVENT, hasToString("IdleStateEvent(ALL_IDLE)"));
    }
}