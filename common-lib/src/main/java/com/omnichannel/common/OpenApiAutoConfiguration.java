package com.omnichannel.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Shared Swagger/OpenAPI setup: a Bearer-JWT "Authorize" button, and the gateway-injected X-User-* headers are
 * hidden from the docs because clients must never send them.
 */
@AutoConfiguration
@ConditionalOnClass({OpenAPI.class, OperationCustomizer.class})
public class OpenApiAutoConfiguration {

    @Bean
    OpenAPI omnichannelOpenApi(@Value("${spring.application.name:service}") String name) {
        return new OpenAPI()
                .info(new Info().title(name).version("1.0.0"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    OperationCustomizer hideTrustedHeaders() {
        return (operation, handlerMethod) -> {
            if (operation.getParameters() != null) {
                operation.getParameters().removeIf(p -> "header".equals(p.getIn()) && p.getName() != null
                        && p.getName().startsWith("X-User-"));
            }
            return operation;
        };
    }
}
