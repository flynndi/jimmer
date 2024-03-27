package org.babyfish.jimmer.sql.runtime;

import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.runtime.ImmutableSpi;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.util.Date;

public class Converters {

    private static final ZoneOffset ZONE_OFFSET = OffsetDateTime.now().getOffset();

    private static final ZoneId ZONE_ID = ZoneId.systemDefault();

    private Converters() {}

    public static Object tryConvert(Object value, ZoneId zoneId, Class<?> expectedType) {
        if (value == null || (value.getClass() == expectedType && ZONE_ID.equals(zoneId))) {
            return value;
        }
        if (value instanceof Number) {
            Number num = (Number) value;
            if (expectedType == boolean.class || expectedType == Boolean.class) {
                return num.intValue() != 0;
            }
            if (expectedType == byte.class || expectedType == Byte.class) {
                return num.byteValue();
            }
            if (expectedType == short.class || expectedType == Short.class) {
                return num.shortValue();
            }
            if (expectedType == int.class || expectedType == Integer.class) {
                return num.intValue();
            }
            if (expectedType == long.class || expectedType == Long.class) {
                return num.longValue();
            }
            if (expectedType == float.class || expectedType == Float.class) {
                return num.floatValue();
            }
            if (expectedType == double.class || expectedType == Double.class) {
                return num.doubleValue();
            }
            if (expectedType == BigInteger.class) {
                if (value instanceof BigDecimal) {
                    return ((BigDecimal)value).toBigInteger();
                }
                return BigInteger.valueOf(((Number) value).longValue());
            }
            if (expectedType == BigDecimal.class) {
                if (value instanceof BigInteger) {
                    return new BigDecimal(value.toString());
                }
                if (value instanceof Double || value instanceof Float) {
                    return BigDecimal.valueOf(((Number) value).longValue());
                }
                return BigDecimal.valueOf(num.doubleValue());
            }
        }
        if (value instanceof Boolean && expectedType == boolean.class) {
            return value;
        }
        if (value instanceof String && (expectedType == char.class || expectedType == Character.class)) {
            return ((String)value).charAt(0);
        }
        if (value instanceof ImmutableSpi && ((ImmutableSpi)value).__type() == ImmutableType.get(expectedType)) {
            return value;
        }
        if (value instanceof Instant) {
            return tryConvertInstant((Instant) value, zoneId, expectedType);
        }
        if (value instanceof java.sql.Date) {
            return tryConvertInstant(Instant.ofEpochMilli(((java.sql.Date) value).getTime()), zoneId, expectedType);
        }
        if (value instanceof java.sql.Time) {
            return tryConvertInstant(Instant.ofEpochMilli(((java.sql.Time) value).getTime()), zoneId, expectedType);
        }
        if (value instanceof Timestamp) {
            return tryConvertInstant(((Timestamp) value).toInstant(), zoneId, expectedType);
        }
        if (value instanceof Date) {
            return tryConvertInstant(((Date) value).toInstant(), zoneId, expectedType);
        }
        if (value instanceof LocalDate) {
            return tryConvertInstant(((LocalDate) value).atTime(LocalTime.MIN).toInstant(ZONE_OFFSET), zoneId, expectedType);
        }
        if (value instanceof LocalTime) {
            return tryConvertInstant(
                    ((LocalTime) value).atDate(LocalDate.of(1970, 1, 1)).toInstant(ZONE_OFFSET),
                    zoneId,
                    expectedType
            );
        }
        if (value instanceof LocalDateTime) {
            return tryConvertInstant(((LocalDateTime) value).toInstant(ZONE_OFFSET), zoneId, expectedType);
        }
        if (value instanceof OffsetDateTime) {
            return tryConvertInstant(((OffsetDateTime) value).toInstant(), zoneId, expectedType);
        }
        if (value instanceof ZonedDateTime) {
            return tryConvertInstant(((ZonedDateTime) value).toInstant(), zoneId, expectedType);
        }
        return null;
    }

    private static Object tryConvertInstant(Instant instant, ZoneId zoneId, Class<?> expectedType) {
        if (expectedType == Instant.class) {
            return instant;
        }
        if (expectedType == Date.class) {
            return new Date(instant.toEpochMilli());
        }
        if (expectedType == java.sql.Date.class) {
            return new java.sql.Date(instant.toEpochMilli());
        }
        if (expectedType == Time.class) {
            return new Time(instant.toEpochMilli());
        }
        if (expectedType == Timestamp.class) {
            return new Timestamp(instant.toEpochMilli());
        }
        if (expectedType == LocalDate.class) {
            return instant.atZone(zoneId).toLocalDate();
        }
        if (expectedType == LocalTime.class) {
            return instant.atZone(zoneId).toLocalTime();
        }
        if (expectedType == LocalDateTime.class) {
            return instant.atZone(zoneId).toLocalDateTime();
        }
        if (expectedType == OffsetDateTime.class) {
            return instant.atZone(zoneId).toOffsetDateTime();
        }
        if (expectedType == ZonedDateTime.class) {
            return instant.atZone(zoneId).toOffsetDateTime().toZonedDateTime();
        }
        return null;
    }
}