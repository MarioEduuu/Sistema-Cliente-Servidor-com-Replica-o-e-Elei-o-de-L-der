package com.unifor.br.chat_peer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class Chat {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final String gatewayUrl;

    public Chat(String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
    }

    public static void main(String[] args) {
        String gateway = args.length > 0 ? args[0] : "http://localhost:8080";
        Chat chat = new Chat(gateway);
        chat.startConsole();
    }

    public void startConsole() {
        System.out.println("Cliente HTTP iniciado.");
        System.out.println("Gateway: " + gatewayUrl);
        System.out.println("Comandos: /health, /exit");
        System.out.println("Digite qualquer outro texto para enviar mensagem.");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    return;
                }

                String input = line.trim();
                if (input.isBlank()) {
                    continue;
                }

                if ("/exit".equalsIgnoreCase(input)) {
                    System.out.println("Encerrando cliente.");
                    return;
                }

                if ("/health".equalsIgnoreCase(input)) {
                    System.out.println(callGateway("/gateway/health", "GET", null));
                    continue;
                }

                String payload = "{\"payload\":\"" + escapeJson(input) + "\"}";
                System.out.println(callGateway("/gateway/forward", "POST", payload));
            }
        } catch (IOException | InterruptedException ex) {
            System.out.println("Falha no cliente: " + ex.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private String callGateway(String path, String method, String body) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(gatewayUrl + path))
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json");

        if ("POST".equalsIgnoreCase(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else {
            builder.GET();
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return "HTTP " + response.statusCode() + " -> " + response.body();
    }

    private String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
