package org.babyfish.jimmer.spring.cfg;

import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author Enaium
 */
public class SwaggerUiConfig implements WebMvcConfigurer {
    private final String path;
    private final String version;

    public SwaggerUiConfig(String uiPath, String version) {
        path = uiPath.substring(0, uiPath.indexOf("/"));
        this.version = version;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(path + "/**")
                .addResourceLocations("classpath:META-INF/resources/webjars/swagger-ui/" + version + "/");
    }
}
