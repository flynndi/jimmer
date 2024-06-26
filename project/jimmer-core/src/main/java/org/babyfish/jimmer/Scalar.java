package org.babyfish.jimmer;

import kotlin.annotation.AnnotationTarget;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generally speaking, this annotation does not need to be used,
 * and the scalar property does not need to be explicitly stated.
 *
 * If the property is of type `List&lt;E&gt;`, it will be treated as
 * special collection property whose element type must be Java class/interface.
 * For example, one-to-many and many-to-many associations, and their `@{@link org.babyfish.jimmer.sql.IdView}` properties.
 *
 * However, sometimes `List&lt;E&gt;` may be just a JSON field, and even nested structures can appear
 * (for example: `List&lt;List&lt;E&gt;&gt;`),
 * this is the need to explicitly use this annotation or any other annotation decorated by this annotation(eg: Serialized).
 */
@Retention(RetentionPolicy.RUNTIME)
@kotlin.annotation.Target(allowedTargets = AnnotationTarget.PROPERTY)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface Scalar {
}
