package org.babyfish.jimmer.apt.dto;

import com.squareup.javapoet.*;
import org.babyfish.jimmer.apt.immutable.generator.Constants;
import org.babyfish.jimmer.apt.immutable.meta.ImmutableProp;
import org.babyfish.jimmer.apt.immutable.meta.ImmutableType;
import org.babyfish.jimmer.dto.compiler.*;
import org.babyfish.jimmer.impl.util.StringUtil;

import javax.lang.model.element.Modifier;

public class InputBuilderGenerator {

    private final DtoGenerator parentGenerator;

    private final DtoType<ImmutableType, ImmutableProp> dtoType;

    private TypeSpec.Builder typeBuilder;

    public InputBuilderGenerator(
            DtoGenerator parentGenerator) {
        this.parentGenerator = parentGenerator;
        this.dtoType = parentGenerator.dtoType;
    }

    public void generate() {
        typeBuilder = TypeSpec
                .classBuilder("Builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC);
        try {
            addAnnotations();
            addMembers();
            parentGenerator.getTypeBuilder().addType(typeBuilder.build());
        } finally {
            typeBuilder = null;
        }
    }

    private void addAnnotations() {
        typeBuilder.addAnnotation(
                AnnotationSpec
                        .builder(org.babyfish.jimmer.apt.immutable.generator.Constants.JSON_POJO_BUILDER_CLASS_NAME)
                        .addMember("withPrefix", "$S", "")
                        .build()
        );

        for (Anno annotation : dtoType.getAnnotations()) {
            if (annotation.getQualifiedName().equals(Constants.JSON_NAMING_CLASS_NAME.canonicalName())) {
                if (!annotation.getValueMap().containsKey("value")) {
                    continue;
                }
                typeBuilder.addAnnotation(
                        AnnotationSpec
                                .builder(Constants.JSON_NAMING_CLASS_NAME)
                                .addMember(
                                        "value", "$T.class",
                                        ClassName.bestGuess(((Anno.TypeRefValue) annotation.getValueMap().get("value")).typeRef.getTypeName()))
                                .build()
                );
            }
        }
    }

    private void addMembers() {
        for (DtoProp<ImmutableType, ImmutableProp> prop : dtoType.getDtoProps()) {
            addField(prop);
            addStateField(prop);
        }
        for (UserProp prop : dtoType.getUserProps()) {
            addField(prop);
        }
        for (DtoProp<ImmutableType, ImmutableProp> prop : dtoType.getDtoProps()) {
            addSetter(prop);
        }
        for (UserProp prop : dtoType.getUserProps()) {
            addSetter(prop);
        }
        addBuild();
    }

    private void addField(AbstractProp prop) {
        TypeName typeName = parentGenerator.getPropTypeName(prop);
        if (isFieldNullable(prop)) {
            typeName = typeName.box();
        }
        FieldSpec.Builder builder = FieldSpec
                .builder(typeName, prop.getName())
                .addModifiers(Modifier.PRIVATE);
        typeBuilder.addField(builder.build());
    }

    private void addStateField(AbstractProp prop) {
        String stateFieldName = parentGenerator.stateFieldName(prop, true);
        if (stateFieldName == null) {
            return;
        }
        FieldSpec.Builder builder = FieldSpec
                .builder(TypeName.BOOLEAN, stateFieldName)
                .addModifiers(Modifier.PRIVATE);
        typeBuilder.addField(builder.build());
    }

    private void addSetter(AbstractProp prop) {
        String stateFieldName = parentGenerator.stateFieldName(prop, true);
        MethodSpec.Builder builder = MethodSpec
                .methodBuilder(prop.getName())
                .addModifiers(Modifier.PUBLIC)
                .addParameter(parentGenerator.getPropTypeName(prop), prop.getName())
                .returns(parentGenerator.getDtoClassName("Builder"));
        if (prop.isNullable()) {
            builder.addStatement(
                    "this.$L = $L",
                    prop.getName(),
                    prop.getName()
            );
            if (stateFieldName != null) {
                builder.addStatement("this.$L = true", stateFieldName);
            }
        } else {
            builder.addStatement(
                    "this.$L = $T.requireNonNull($L, $S)",
                    prop.getName(),
                    Constants.OBJECTS_CLASS_NAME,
                    prop.getName(),
                    "The property \"" +
                            prop.getName() +
                            "\" cannot be null"
            );
        }
        builder.addStatement("return this");
        typeBuilder.addMethod(builder.build());
    }

    private void addBuild() {
        ClassName dtoClassName = parentGenerator.getDtoClassName(null);
        MethodSpec.Builder builder = MethodSpec
                .methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(dtoClassName);
        builder.addStatement("$T _input = new $T()", dtoClassName, dtoClassName);
        for (DtoProp<ImmutableType, ImmutableProp> prop : dtoType.getDtoProps()) {
            if (!prop.isNullable()) {
                builder.beginControlFlow("if ($L == null)", prop.getName());
                builder.addStatement(
                        "throw $T.$L($T.class, $S)",
                        Constants.INPUT_CLASS_NAME,
                        "unknownNonNullProperty",
                        dtoClassName,
                        prop.getName()
                );
                builder.endControlFlow();
                builder.addStatement(
                        "_input.$L($L)",
                        StringUtil.identifier("set", prop.getName()),
                        prop.getName()
                );
            } else {
                String stateFieldName = parentGenerator.stateFieldName(prop, true);
                switch (prop.getInputModifier()) {
                    case FIXED:
                        builder.beginControlFlow("if (!$L)", stateFieldName);
                        builder.addStatement(
                                "throw $T.$L($T.class, $S)",
                                Constants.INPUT_CLASS_NAME,
                                "unknownNullableProperty",
                                dtoClassName,
                                prop.getName()
                        );
                        builder.endControlFlow();
                    case STATIC:
                        builder.addStatement(
                                "_input.$L($L)",
                                StringUtil.identifier("set", prop.getName()),
                                prop.getName()
                        );
                        break;
                    case DYNAMIC:
                        builder.beginControlFlow("if ($L)", stateFieldName);
                        builder.addStatement(
                                "_input.$L($L)",
                                StringUtil.identifier("set", prop.getName()),
                                prop.getName()
                        );
                        builder.endControlFlow();
                        break;
                    case FUZZY:
                        builder.beginControlFlow("if ($L != null)", prop.getName());
                        builder.addStatement(
                                "_input.$L($L)",
                                StringUtil.identifier("set", prop.getName()),
                                prop.getName()
                        );
                        builder.endControlFlow();
                        break;
                }
            }
        }
        for (UserProp prop : dtoType.getUserProps()) {
            builder.addStatement(
                    "_input.$L($L)",
                    StringUtil.identifier("set", prop.getName()),
                    prop.getName()
            );
        }
        builder.addStatement("return _input");
        typeBuilder.addMethod(builder.build());
    }

    private static boolean isFieldNullable(AbstractProp prop) {
        if (prop instanceof DtoProp<?, ?>) {
            String funcName = ((DtoProp<?, ?>) prop).getFuncName();
            return !"null".equals(funcName) && !"notNull".equals(funcName);
        }
        return true;
    }
}
