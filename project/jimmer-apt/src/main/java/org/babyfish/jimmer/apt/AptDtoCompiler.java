package org.babyfish.jimmer.apt;

import org.babyfish.jimmer.apt.meta.ImmutableProp;
import org.babyfish.jimmer.apt.meta.ImmutableType;
import org.babyfish.jimmer.meta.impl.dto.ast.DtoCompiler;
import org.babyfish.jimmer.sql.Id;

import java.util.Map;

public class AptDtoCompiler extends DtoCompiler<ImmutableType, ImmutableProp> {

    protected AptDtoCompiler(ImmutableType baseType) {
        super(baseType);
    }

    @Override
    protected boolean isEntity(ImmutableType baseType) {
        return baseType.isEntity();
    }

    @Override
    protected ImmutableType getSuperType(ImmutableType baseType) {
        return baseType.getSuperType();
    }

    @Override
    protected Map<String, ImmutableProp> getDeclaredProps(ImmutableType baseType) {
        return baseType.getDeclaredProps();
    }

    @Override
    protected Map<String, ImmutableProp> getProps(ImmutableType baseType) {
        return baseType.getProps();
    }

    @Override
    protected boolean isMappable(ImmutableProp baseProp) {
        return !baseProp.isTransient() || baseProp.hasTransientResolver();
    }

    @Override
    protected boolean isNullable(ImmutableProp baseProp) {
        return baseProp.isNullable();
    }

    @Override
    protected boolean isId(ImmutableProp baseProp) {
        return baseProp.getAnnotation(Id.class) != null;
    }

    @Override
    protected boolean isList(ImmutableProp baseProp) {
        return baseProp.isList();
    }

    @Override
    protected ImmutableType getTargetType(ImmutableProp baseProp) {
        return baseProp.getTargetType();
    }
}
