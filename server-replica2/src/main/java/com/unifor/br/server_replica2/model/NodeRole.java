package com.unifor.br.server_replica2.model;

public enum NodeRole {
    PRIMARY,
    REPLICA;

    public static NodeRole fromValue(String raw) {
        if (raw == null) {
            return REPLICA;
        }

        for (NodeRole role : values()) {
            if (role.name().equalsIgnoreCase(raw.trim())) {
                return role;
            }
        }

        return REPLICA;
    }
}
