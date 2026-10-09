package com.direkjames.dkchat.user;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player chat state. Thread-safe because chat events run off the main thread. */
public final class ChatUser {

    private volatile boolean spyDisabled;
    private volatile UUID replyTarget;
    private final Set<UUID> ignored = ConcurrentHashMap.newKeySet();

    public boolean spyDisabled() {
        return spyDisabled;
    }

    void spyDisabled(boolean value) {
        this.spyDisabled = value;
    }

    public UUID replyTarget() {
        return replyTarget;
    }

    public void replyTarget(UUID target) {
        this.replyTarget = target;
    }

    public Set<UUID> ignored() {
        return ignored;
    }
}
