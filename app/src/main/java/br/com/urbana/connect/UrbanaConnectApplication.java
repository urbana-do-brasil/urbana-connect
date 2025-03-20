package br.com.urbana.connect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Classe principal da aplicação Urbana Connect WhatsApp Chatbot.
 * Inicia o aplicativo Spring Boot e configura os componentes necessários.
 */
@SpringBootApplication
@EnableMongoRepositories
@ConfigurationPropertiesScan
public class UrbanaConnectApplication {

    /**
     * Método principal que inicia a aplicação.
     *
     * @param args Argumentos de linha de comando
     */
    public static void main(String[] args) {
        SpringApplication.run(UrbanaConnectApplication.class, args);
    }
} 