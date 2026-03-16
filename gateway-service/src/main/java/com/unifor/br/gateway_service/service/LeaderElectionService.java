package com.unifor.br.gateway_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LeaderElectionService {

    private final Node primaryNode;
    private final List<Node> replicaNodes;
    private final List<Node> allNodes;
    private final RestTemplate restTemplate;

    private Node currentLeader;
    private boolean primaryWasDemoted;

    public LeaderElectionService(
            @Value("${gateway.nodes.primary:http://localhost:8081}") String primaryUrl,
            @Value("${gateway.nodes.replica1:http://localhost:8082}") String replica1Url,
            @Value("${gateway.nodes.replica2:http://localhost:8083}") String replica2Url,
            @Value("${gateway.request-timeout-ms:1500}") int timeoutMs
    ) {
        this.primaryNode = new Node("primary", primaryUrl, 0);
        Node replica1 = new Node("replica1", replica1Url, 1);
        Node replica2 = new Node("replica2", replica2Url, 2);

        this.replicaNodes = List.of(replica1, replica2);
        this.allNodes = List.of(primaryNode, replica1, replica2);
        this.restTemplate = createRestTemplate(timeoutMs);
        this.currentLeader = primaryNode;
    }

    public synchronized Map<String, Object> forward(String payload) {
        Node leader = resolveLeader();

        try {
            Map<String, Object> saved = sendToLeader(leader, payload);
            return Map.of(
                    "status", "DELIVERED",
                    "leader", leader.id,
                    "leaderUrl", leader.url,
                    "message", saved
            );
        } catch (RestClientException firstFailure) {
            Node nextLeader = electLeader(leader.id);
            if (nextLeader == null) {
                throw new IllegalStateException("Nenhum servidor disponivel para processar a mensagem.");
            }

            Map<String, Object> saved = sendToLeader(nextLeader, payload);
            return Map.of(
                    "status", "DELIVERED_AFTER_FAILOVER",
                    "leader", nextLeader.id,
                    "leaderUrl", nextLeader.url,
                    "message", saved
            );
        }
    }

    public synchronized Map<String, Object> leaderStatus() {
        Node leader = resolveLeader();
        return Map.of(
                "leader", leader.id,
                "leaderUrl", leader.url
        );
    }

    public synchronized Map<String, Object> clusterStatus() {
        Node leader = resolveLeader();
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Node node : allNodes) {
            nodes.add(Map.of(
                    "id", node.id,
                    "url", node.url,
                    "healthy", isHealthy(node)
            ));
        }

        return Map.of(
                "status", "UP",
                "leader", leader.id,
                "leaderUrl", leader.url,
                "nodes", nodes
        );
    }

    private Node resolveLeader() {
        if (currentLeader != null && isHealthy(currentLeader)) {
            reintegrateOldPrimaryIfNeeded(currentLeader);
            return currentLeader;
        }

        if (currentLeader != null && "primary".equals(currentLeader.id)) {
            primaryWasDemoted = true;
        }

        Node elected = electLeader(currentLeader == null ? null : currentLeader.id);
        if (elected == null) {
            throw new IllegalStateException("Nenhum servidor disponivel no cluster.");
        }

        reintegrateOldPrimaryIfNeeded(elected);
        return elected;
    }

    private Node electLeader(String excludedNodeId) {
        if (isHealthy(primaryNode) && !primaryNode.id.equals(excludedNodeId)) {
            promote(primaryNode);
            return primaryNode;
        }

        primaryWasDemoted = true;
        for (Node replica : replicaNodes) {
            if (!replica.id.equals(excludedNodeId) && isHealthy(replica)) {
                promote(replica);
                return replica;
            }
        }

        for (Node replica : replicaNodes) {
            if (isHealthy(replica)) {
                promote(replica);
                return replica;
            }
        }

        if (isHealthy(primaryNode)) {
            promote(primaryNode);
            primaryWasDemoted = false;
            return primaryNode;
        }

        return null;
    }

    private void promote(Node leader) {
        updateRole(leader, "PRIMARY");
        for (Node node : allNodes) {
            if (!node.id.equals(leader.id) && isHealthy(node)) {
                updateRole(node, "REPLICA");
            }
        }
        currentLeader = leader;
    }

    private void reintegrateOldPrimaryIfNeeded(Node leader) {
        if (!primaryWasDemoted || "primary".equals(leader.id)) {
            return;
        }

        if (!isHealthy(primaryNode)) {
            return;
        }

        List<Map<String, Object>> snapshot = fetchMessages(leader);
        if (snapshot == null) {
            return;
        }

        syncNode(primaryNode, snapshot);
        updateRole(primaryNode, "REPLICA");
        primaryWasDemoted = false;
    }

    private Map<String, Object> sendToLeader(Node leader, String payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("payload", payload), headers);

        return restTemplate.exchange(
                leader.url + "/save",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {
                }
        ).getBody();
    }

    private List<Map<String, Object>> fetchMessages(Node leader) {
        try {
            return restTemplate.exchange(
                    leader.url + "/messages",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    }
            ).getBody();
        } catch (RestClientException ex) {
            return null;
        }
    }

    private void syncNode(Node target, List<Map<String, Object>> snapshot) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", snapshot);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.exchange(target.url + "/sync", HttpMethod.POST, request, Void.class);
        } catch (RestClientException ignored) {
            // Sync sera tentado novamente em uma proxima requisicao ao gateway.
        }
    }

    private void updateRole(Node node, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("role", role), headers);

        try {
            restTemplate.exchange(node.url + "/role", HttpMethod.POST, request, Void.class);
        } catch (RestClientException ignored) {
            // Falhas de role sao tratadas pelo proximo ciclo de eleicao.
        }
    }

    private boolean isHealthy(Node node) {
        try {
            Map<String, String> response = restTemplate.exchange(
                    node.url + "/health",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, String>>() {
                    }
            ).getBody();

            return response != null && "UP".equalsIgnoreCase(response.get("status"));
        } catch (RestClientException ex) {
            return false;
        }
    }

    private RestTemplate createRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        return new RestTemplate(requestFactory);
    }

    private static final class Node {
        private final String id;
        private final String url;
        private final int priority;

        private Node(String id, String url, int priority) {
            this.id = id;
            this.url = url;
            this.priority = priority;
        }
    }
}
