package com.unifor.br.server_replica2.controller;

import com.unifor.br.server_replica2.model.MessageEntry;
import com.unifor.br.server_replica2.model.MessageRequest;
import com.unifor.br.server_replica2.model.RoleUpdateRequest;
import com.unifor.br.server_replica2.model.SyncRequest;
import com.unifor.br.server_replica2.service.NodeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
public class NodeController {

    private final NodeService nodeService;

    public NodeController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @PostMapping("/save")
    public ResponseEntity<?> save(@RequestBody MessageRequest request) throws IOException {
        if (request == null || request.getPayload() == null || request.getPayload().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "payload e obrigatorio"));
        }

        try {
            MessageEntry saved = nodeService.saveMessage(request.getPayload());
            return ResponseEntity.ok(saved);
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", ex.getMessage(),
                    "role", nodeService.getCurrentRole().name()
            ));
        }
    }

    @PostMapping("/replicate")
    public ResponseEntity<?> replicate(@RequestBody MessageEntry message) throws IOException {
        if (message == null || message.getPayload() == null || message.getPayload().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "mensagem invalida"));
        }

        MessageEntry saved = nodeService.saveReplicaMessage(message);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/messages")
    public List<MessageEntry> listMessages() throws IOException {
        return nodeService.findAll();
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync(@RequestBody SyncRequest request) throws IOException {
        List<MessageEntry> messages = request == null || request.getMessages() == null ? List.of() : request.getMessages();
        nodeService.sync(messages);
        return ResponseEntity.ok(Map.of("status", "SYNCED", "total", messages.size()));
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "nodeId", nodeService.getNodeId(),
                "role", nodeService.getCurrentRole().name()
        );
    }

    @GetMapping("/role")
    public Map<String, String> role() {
        return Map.of("role", nodeService.getCurrentRole().name());
    }

    @PostMapping("/role")
    public ResponseEntity<Map<String, String>> updateRole(@RequestBody RoleUpdateRequest request) {
        if (request == null || request.getRole() == null || request.getRole().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "role e obrigatorio"));
        }

        nodeService.updateRole(request.getRole());
        return ResponseEntity.ok(Map.of(
                "status", "UPDATED",
                "role", nodeService.getCurrentRole().name(),
                "nodeId", nodeService.getNodeId()
        ));
    }
}
