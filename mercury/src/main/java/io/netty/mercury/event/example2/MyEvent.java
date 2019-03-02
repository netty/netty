package io.netty.mercury.event.example2;

import java.util.EventObject;

public class MyEvent extends EventObject {

    private int state;

    /**
     * Constructs a prototypical Event.
     *
     * @param source The object on which the Event initially occurred.
     * @throws IllegalArgumentException if source is null.
     */
    public MyEvent(Object source) {
        super(source);
        this.state = ((EventSource) source).getState();
    }

    public int getState() {
        return state;
    }
}
