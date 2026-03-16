package com.unifor.br.server_replica2.service;

import com.unifor.br.server_replica2.model.MessageEntry;
import com.unifor.br.server_replica2.model.NodeRole;
import com.unifor.br.server_replica2.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class NodeService {

    private final MessageRepository repository;
    private final String nodeId;
    private final List<String> peerUrls;
    private final RestTemplate restTemplate;
    private final AtomicReference<NodeRole> currentRole;

    public NodeService(
            MessageRepository repository,
            @Value("${node.id:unknown}") String nodeId,
            @Value("${node.role:REPLICA}") String configuredRole,
            @Value("${node.peers:}") String configuredPeers,
            @Value("${request.timeout.ms:1500}") int timeoutMs
    ) {
        this.repository = repository;
        this.nodeId = nodeId;
        this.peerUrls = parsePeers(configuredPeers);
        this.restTemplate = createRestTemplate(timeoutMs);
        this.currentRole = new AtomicReference<>(NodeRole.fromValue(configuredRole));
    }

    public MessageEntry saveMessage(String payload) throws IOException {
        if (currentRole.get() != NodeRole.PRIMARY) {
            throw new IllegalStateException("Este no nao e primaria ativa.");
        }

        MessageEntry message = new MessageEntry(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                payload
        );

        repository.saveIfAbsent(message);
        replicateToPeers(message);
        return message;
    }

    public MessageEntry saveReplicaMessage(MessageEntry message) throws IOException {
        if (message.getId() == null || message.getId().isBlank()) {
            message.setId(UUID.randomUUID().toString());
        }
        if (message.getCreatedAt() <= 0) {
            message.setCreatedAt(System.currentTimeMillis());
        }
        repository.saveIfAbsent(message);
        return message;
    }

    public List<MessageEntry> findAll() throws IOException {
        return repository.findAll();
    }

    public void sync(List<MessageEntry> messages) throws IOException {
        repository.replaceAll(messages);
    }

    public NodeRole getCurrentRole() {
        return currentRole.get();
    }

    public void updateRole(String newRole) {
        currentRole.set(NodeRole.fromValue(newRole));
    }

    public String getNodeId() {
        return nodeId;
    }

    private void replicateToPeers(MessageEntry message) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<MessageEntry> request = new HttpEntity<>(message, headers);

        for (String peerUrl : peerUrls) {
            try {
                restTemplate.exchange(peerUrl + "/replicate", HttpMethod.POST, request, Void.class);
            } catch (RestClientException ignored) {
                // Replica indisponivel nao interrompe a escrita local.
            }
        }
    }

    private RestTemplate createRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        return new RestTemplate(requestFactory);
    }

    private List<String> parsePeers(String configuredPeers) {
        if (configuredPeers == null || configuredPeers.isBlank()) {
            return Collections.emptyList();
        }

        List<String> peers = new ArrayList<>();
        for (String value : configuredPeers.split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                peers.add(trimmed);
            }
        }
        return peers;
    }
}
