package org.babyfish.jimmer.jackson.codec;

public interface JsonCodecCustomization {
    void customizeV2(com.fasterxml.jackson.databind.json.JsonMapper.Builder builder);

    void customizeV3(tools.jackson.databind.json.JsonMapper.Builder builder);
}
