package br.com.urbana.connect.application.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/** Ensures Mongo indexes declared by the persistence documents exist at startup. */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
public class MongoIndexConfiguration {

    @Bean
    ApplicationRunner mongoIndexInitializer(MongoTemplate mongoTemplate, MongoMappingContext mappingContext) {
        return args -> {
            MongoPersistentEntityIndexResolver resolver = new MongoPersistentEntityIndexResolver(mappingContext);

            try {
                mappingContext.getPersistentEntities().stream()
                        .filter(entity -> entity.getType().isAnnotationPresent(
                                org.springframework.data.mongodb.core.mapping.Document.class))
                        .flatMap(entity -> resolver.resolveIndexForEntity(entity).stream())
                        .forEach(index -> mongoTemplate.indexOps(index.getCollection()).ensureIndex(index));
            } catch (RuntimeException exception) {
                throw new IllegalStateException(
                        "Mongo index provisioning failed; application startup cannot continue", exception);
            }
        };
    }
}
