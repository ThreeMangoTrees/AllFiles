package com.convertx.heictopdf;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fileOperationsXXXOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FileOperationsXXX PDF Tools API")
                        .description("API for converting supported files to PDF, compressing PDFs, and merging multiple PDFs.")
                        .version("v1")
                        .contact(new Contact().name("FileOperationsXXX"))
                        .license(new License().name("Internal Use")));
    }
}
