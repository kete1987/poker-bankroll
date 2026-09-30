package io.github.kete1987.pokerbankroll.common.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI openApi(ObjectProvider<BuildProperties> build) {
        String version = build.stream().map(BuildProperties::getVersion).findFirst().orElse("dev");
        return new OpenAPI().info(new Info()
                .title("poker-bankroll API")
                .description("REST API of poker-bankroll, a self-hosted poker bankroll manager.")
                .version(version)
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }
}
