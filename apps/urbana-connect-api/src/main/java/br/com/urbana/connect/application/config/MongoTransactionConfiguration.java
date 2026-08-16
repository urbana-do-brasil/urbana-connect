package br.com.urbana.connect.application.config;

import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Enables the Mongo transaction boundary used by reception persistence.
 *
 * <p>The URI remains responsible for selecting the replica set. The
 * customizer keeps retry behavior enabled even when a deployment supplies a
 * URI without the explicit retry query parameters.</p>
 */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
public class MongoTransactionConfiguration {

    @Bean
    MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory mongoDatabaseFactory) {
        return new MongoTransactionManager(mongoDatabaseFactory);
    }

    @Bean
    MongoClientSettingsBuilderCustomizer mongoClientSettingsBuilderCustomizer() {
        return builder -> builder
                .retryWrites(true)
                .retryReads(true);
    }
}
