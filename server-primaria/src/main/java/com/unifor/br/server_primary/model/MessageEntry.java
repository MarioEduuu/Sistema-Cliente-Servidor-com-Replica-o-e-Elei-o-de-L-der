package com.unifor.br.server_primary.model;

public class MessageEntry {
    private String id;
    private long createdAt;
    private String payload;

    public MessageEntry() {
    }

    public MessageEntry(String id, long createdAt, String payload) {
        this.id = id;
        this.createdAt = createdAt;
        this.payload = payload;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
