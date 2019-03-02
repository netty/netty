package io.netty.mercury.event.example2;

import java.util.EventListener;
import java.util.HashSet;
import java.util.Set;

public class EventSource {

    private int state;
    private String name;
    private Set<EventListener> eventListeners = new HashSet<EventListener>();

    public void addListener(EventListener eventListener) {
        eventListeners.add(eventListener);
    }

    public void nofity() {
        for (EventListener eventListener : eventListeners) {
            ((TrueStateListener) eventListener).handle(new MyEvent(this));
        }
    }


    public void changeState(int state) {
        System.out.println("----change state ing ------");
        this.setState(state);
        nofity();
    }

    public EventSource() {
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
        nofity();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
