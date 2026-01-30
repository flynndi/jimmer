package org.babyfish.jimmer.spring.cfg;

import org.babyfish.jimmer.spring.repository.config.JimmerRepositoriesConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
})
@EnableConfigurationProperties(JimmerProperties.class)
@Import({
        SqlClientConfig.class,
        JimmerRepositoriesConfig.class,
        ErrorTranslatorConfig.class,
        JimmerJacksonConfig.class
})
public class JimmerAutoConfiguration {
}

