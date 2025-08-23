package com.example.natsclient.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InfoProperties.class)
public class OpenApiConfig {

    private final InfoProperties infoProperties;

    public OpenApiConfig(InfoProperties infoProperties) {
        this.infoProperties = infoProperties;
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(infoProperties.getName() + " API")
                        .version(infoProperties.getVersion())
                        .description(infoProperties.getDescription() + " API Documentation"));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/**")
                .build();
    }
}