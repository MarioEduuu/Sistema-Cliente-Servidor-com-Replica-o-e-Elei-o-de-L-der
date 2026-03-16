package com.unifor.br.chat_peer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ChatPeerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatPeerApplication.class, args);
	}

	@Bean
	CommandLineRunner commandLineRunner(
			@Value("${client.console.enabled:false}") boolean consoleEnabled,
			@Value("${client.gateway-url:http://localhost:8080}") String gatewayUrl
	) {
		return args -> {
			if (consoleEnabled) {
				new Chat(gatewayUrl).startConsole();
			}
		};
	}
}
