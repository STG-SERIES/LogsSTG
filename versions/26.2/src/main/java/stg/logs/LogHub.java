package stg.logs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class LogHub {
    private final int capacity;
    private final ArrayDeque<String> history = new ArrayDeque<>();
    private final Set<Subscriber> subscribers = ConcurrentHashMap.newKeySet();
    private final Object lock = new Object();

    LogHub(int capacity) {
        this.capacity = capacity;
    }

    void publish(String line) {
        List<Subscriber> targets;
        synchronized (lock) {
            while (history.size() >= capacity) {
                history.removeFirst();
            }
            history.addLast(line);
            targets = new ArrayList<>(subscribers);
        }
        for (Subscriber subscriber : targets) {
            subscriber.accept(line);
        }
    }

    List<String> join(Subscriber subscriber) {
        synchronized (lock) {
            List<String> snapshot = List.copyOf(history);
            subscribers.add(subscriber);
            return snapshot;
        }
    }

    void leave(Subscriber subscriber) {
        synchronized (lock) {
            subscribers.remove(subscriber);
        }
    }

    interface Subscriber {
        void accept(String line);
    }
}
