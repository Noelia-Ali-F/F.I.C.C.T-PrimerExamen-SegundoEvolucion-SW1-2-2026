package com.arqnova.repositorios.dynamodb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import java.net.URI;
import java.time.Duration;

@Configuration
@Profile("dynamodb")
public class DynamoConfiguration {
    @Bean(destroyMethod = "close") DynamoDbClient dynamoDbClient(@Value("${app.dynamodb.region}") String region,
            @Value("${app.dynamodb.endpoint:}") String endpoint) {
        var builder = DynamoDbClient.builder().region(Region.of(region))
                .overrideConfiguration(ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(30)).apiCallAttemptTimeout(Duration.ofSeconds(15)).build())
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(20)));
        if (!endpoint.isBlank()) {
            URI uri = URI.create(endpoint);
            if (!uri.getHost().equals("localhost") && !uri.getHost().equals("127.0.0.1") && !uri.getHost().equals("dynamodb-local"))
                throw new IllegalArgumentException("El endpoint local solo admite localhost o dynamodb-local");
            builder.endpointOverride(uri).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")));
        } else builder.credentialsProvider(DefaultCredentialsProvider.create());
        return builder.build();
    }
    @Bean DynamoDocumentStore dynamoDocumentStore(DynamoDbClient client, ObjectMapper json,
            @Value("${app.dynamodb.table}") String table) {
        var schema = client.describeTable(r -> r.tableName(table)).table().keySchema();
        if (schema.size() != 2 || schema.stream().noneMatch(k -> k.attributeName().equals("pk") && k.keyTypeAsString().equals("HASH"))
                || schema.stream().noneMatch(k -> k.attributeName().equals("sk") && k.keyTypeAsString().equals("RANGE")))
            throw new IllegalStateException("DynamoDB requiere pk (HASH) y sk (RANGE)");
        return new DynamoDocumentStore(client, table, json);
    }
    @Bean PlatformTransactionManager transactionManager(DynamoDocumentStore store) { return new DynamoTransactionManager(store); }
}
