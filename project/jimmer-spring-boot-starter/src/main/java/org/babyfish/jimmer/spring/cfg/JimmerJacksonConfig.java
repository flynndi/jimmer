package org.babyfish.jimmer.spring.cfg;

import org.babyfish.jimmer.jackson.v2.ImmutableModuleV2;
import org.babyfish.jimmer.jackson.v2.JsonCodecV2;
import org.babyfish.jimmer.jackson.v3.ImmutableModuleV3;
import org.babyfish.jimmer.jackson.v3.JsonCodecV3;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JimmerJacksonConfig {

    @ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")
    @ConditionalOnMissingBean(ImmutableModuleV2.class)
    @Configuration(proxyBeanMethods = false)
    protected static class JacksonConfigV2 {
        @Bean
        public ImmutableModuleV2 immutableModuleV2() {
            return new ImmutableModuleV2();
        }
    }

    @ConditionalOnClass(name = "tools.jackson.databind.ObjectMapper")
    @ConditionalOnMissingBean(ImmutableModuleV3.class)
    @Configuration(proxyBeanMethods = false)
    protected static class JacksonConfigV3 {
        @Bean
        public ImmutableModuleV3 immutableModuleV3() {
            return new ImmutableModuleV3();
        }
    }

    @ConditionalOnBean(name = "com.fasterxml.jackson.databind.json.JsonMapper")
    @ConditionalOnMissingBean(JsonCodecV2.class)
    @Configuration(proxyBeanMethods = false)
    protected static class JsonCodecConfigV2 {
        @Bean
        public JsonCodecV2 jsonCodecV2(com.fasterxml.jackson.databind.json.JsonMapper mapper) {
            return new JsonCodecV2(mapper);
        }
    }

    @ConditionalOnClass(name = "tools.jackson.databind.json.JsonMapper")
    @ConditionalOnMissingBean(JsonCodecV3.class)
    @Configuration(proxyBeanMethods = false)
    protected static class JsonCodecConfigV3 {
        @Bean
        public JsonCodecV3 jsonCodecV3(tools.jackson.databind.json.JsonMapper mapper) {
            return new JsonCodecV3(mapper);
        }
    }
}
