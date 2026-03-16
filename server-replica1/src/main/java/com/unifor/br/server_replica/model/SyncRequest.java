package com.unifor.br.server_replica.model;

import java.util.List;

public class SyncRequest {
    private List<MessageEntry> messages;

    public List<MessageEntry> getMessages() {
        return messages;
    }

    public void setMessages(List<MessageEntry> messages) {
        this.messages = messages;
    }
}
