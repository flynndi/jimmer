package org.babyfish.jimmer.jackson.codec;

import java.util.Iterator;
import java.util.Map;

public interface Node {
    Iterator<Map.Entry<String, Node>> fieldsIterator();

    boolean isNull();

    boolean canCastTo(Class<?> type);

    <T> T castTo(Class<T> type);

    <T> T convertTo(Class<T> targetType, JsonConverter converter) throws Exception;
}
