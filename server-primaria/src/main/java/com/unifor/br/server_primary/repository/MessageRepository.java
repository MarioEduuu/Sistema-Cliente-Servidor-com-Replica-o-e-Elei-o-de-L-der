package com.unifor.br.server_primary.repository;

import com.unifor.br.server_primary.model.MessageEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class MessageRepository {

    private final Path filePath;

    public MessageRepository(@Value("${storage.file:data/database.txt}") String fileName) {
        this.filePath = Path.of(fileName);
    }

    public synchronized void saveIfAbsent(MessageEntry message) throws IOException {
        ensureStorage();

        Set<String> ids = new HashSet<>();
        for (String line : Files.readAllLines(filePath, StandardCharsets.UTF_8)) {
            MessageEntry existing = deserialize(line);
            if (existing != null && existing.getId() != null) {
                ids.add(existing.getId());
            }
        }

        if (message.getId() != null && ids.contains(message.getId())) {
            return;
        }

        Files.writeString(
                filePath,
                serialize(message) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    public synchronized List<MessageEntry> findAll() throws IOException {
        ensureStorage();

        List<MessageEntry> messages = new ArrayList<>();
        for (String line : Files.readAllLines(filePath, StandardCharsets.UTF_8)) {
            MessageEntry parsed = deserialize(line);
            if (parsed != null) {
                messages.add(parsed);
            }
        }
        return messages;
    }

    public synchronized void replaceAll(List<MessageEntry> messages) throws IOException {
        ensureStorage();

        List<String> lines = new ArrayList<>();
        for (MessageEntry message : messages) {
            if (message != null && message.getPayload() != null && !message.getPayload().isBlank()) {
                lines.add(serialize(message));
            }
        }

        Files.write(
                filePath,
                lines,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    private void ensureStorage() throws IOException {
        Path parent = filePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (!Files.exists(filePath)) {
            Files.createFile(filePath);
        }
    }

    private String serialize(MessageEntry message) {
        String payload = message.getPayload() == null ? "" : message.getPayload();
        String encodedPayload = Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return message.getId() + "|" + message.getCreatedAt() + "|" + encodedPayload;
    }

    private MessageEntry deserialize(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        String[] parts = line.split("\\|", 3);
        if (parts.length < 3) {
            return null;
        }

        try {
            String payload = new String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8);
            return new MessageEntry(parts[0], Long.parseLong(parts[1]), payload);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
