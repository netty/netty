package io.netty.mercury.event.example1;

import java.util.EventObject;

public class WeChatMessageEvent extends EventObject {

    private String name;

    /**
     * Constructs a prototypical Event.
     *
     * @param source The object on which the Event initially occurred.
     * @throws IllegalArgumentException if source is null.
     */
    public WeChatMessageEvent(Object source, String name) {
        super(source);
        this.name = name;
    }


    @Override
    public Object getSource() {
        return super.getSource();
    }
}
