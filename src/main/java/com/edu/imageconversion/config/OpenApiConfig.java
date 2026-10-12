package com.edu.imageconversion.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Image Conversion & News Aggregation API")
                        .description("API for converting images and aggregating news from multiple sources in parallel")
                        .version("1.0"))
                .components(new Components().addSchemas("MultipartFileArray", new Schema()
                        .type("array")
                        .items(new Schema().type("string").format("binary"))));
    }
}