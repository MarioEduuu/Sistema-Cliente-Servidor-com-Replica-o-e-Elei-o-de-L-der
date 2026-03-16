package com.unifor.br.gateway_service.controller;

import com.unifor.br.gateway_service.model.MessageRequest;
import com.unifor.br.gateway_service.service.LeaderElectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/gateway")
public class GatewayController {

    private final LeaderElectionService leaderElectionService;

    public GatewayController(LeaderElectionService leaderElectionService) {
        this.leaderElectionService = leaderElectionService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(leaderElectionService.clusterStatus());
    }

    @GetMapping("/leader")
    public ResponseEntity<Map<String, Object>> leader() {
        return ResponseEntity.ok(leaderElectionService.leaderStatus());
    }

    @PostMapping("/forward")
    public ResponseEntity<?> forward(@RequestBody MessageRequest request) {
        if (request == null || request.getPayload() == null || request.getPayload().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "payload e obrigatorio"));
        }

        try {
            return ResponseEntity.ok(leaderElectionService.forward(request.getPayload()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(503).body(Map.of("error", ex.getMessage()));
        }
    }
}
