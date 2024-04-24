package org.babyfish.jimmer.ksp.dto

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import org.babyfish.jimmer.client.ApiIgnore
import org.babyfish.jimmer.client.meta.Doc
import org.babyfish.jimmer.dto.compiler.*
import org.babyfish.jimmer.dto.compiler.Anno.*
import org.babyfish.jimmer.impl.util.StringUtil
import org.babyfish.jimmer.impl.util.StringUtil.SnakeCase
import org.babyfish.jimmer.ksp.*
import org.babyfish.jimmer.ksp.immutable.generator.*
import org.babyfish.jimmer.ksp.immutable.meta.ImmutableProp
import org.babyfish.jimmer.ksp.immutable.meta.ImmutableType
import org.babyfish.jimmer.ksp.util.ConverterMetadata
import org.babyfish.jimmer.ksp.util.generatedAnnotation
import java.io.OutputStreamWriter
import java.util.*
import kotlin.math.min

class DtoGenerator private constructor(
    private val ctx: Context,
    private val mutable: Boolean,
    val dtoType: DtoType<ImmutableType, ImmutableProp>,
    private val codeGenerator: CodeGenerator?,
    private val parent: DtoGenerator?,
    private val innerClassName: String?
) {
    private val root: DtoGenerator = parent?.root ?: this

    private val document: Document = Document(dtoType)

    private val useSiteTargetMap = mutableMapOf<String, AnnotationUseSiteTarget>()

    private val interfacePropNames = abstractPropNames(ctx, dtoType)

    init {
        if ((codeGenerator === null) == (parent === null)) {
            throw IllegalArgumentException("The nullity values of `codeGenerator` and `parent` cannot be same")
        }
        if ((parent === null) != (innerClassName === null)) {
            throw IllegalArgumentException("The nullity values of `parent` and `innerClassName` must be same")
        }
    }

    private var _typeBuilder: TypeSpec.Builder? = null

    constructor(
        ctx: Context,
        mutable: Boolean,
        dtoType: DtoType<ImmutableType, ImmutableProp>,
        codeGenerator: CodeGenerator?,
    ): this(ctx, mutable, dtoType, codeGenerator, null, null)

    val typeBuilder: TypeSpec.Builder
        get() = _typeBuilder ?: error("Type builder is not ready")

    fun getDtoClassName(nestedSimpleName: String? = null): ClassName {
        if (innerClassName !== null) {
            val list: MutableList<String> = ArrayList()
            collectNames(list)
            return ClassName(
                root.dtoType.packageName,
                list[0],
                *list.subList(1, list.size).let {
                    if (nestedSimpleName == null) {
                        it
                    } else {
                        it.toMutableList() + nestedSimpleName
                    }
                }.toTypedArray()
            )
        }
        if  (nestedSimpleName == null) {
            return ClassName(
                root.dtoType.packageName,
                dtoType.name!!
            )
        }
        return ClassName(
            root.dtoType.packageName,
            dtoType.name!!,
            nestedSimpleName
        )
    }

    fun generate(allFiles: List<KSFile>) {
        if (codeGenerator != null) {
            codeGenerator.createNewFile(
                Dependencies(false, *allFiles.toTypedArray()),
                root.dtoType.packageName,
                dtoType.name!!
            ).use {
                val fileSpec = FileSpec
                    .builder(
                        root.dtoType.packageName,
                        dtoType.name!!
                    ).apply {
                        indent("    ")
                        addImports()
                        val builder = TypeSpec
                            .classBuilder(dtoType.name!!)
                            .addModifiers(KModifier.OPEN)
                        if (parent == null) {
                            builder.addAnnotation(generatedAnnotation(dtoType.dtoFile, mutable))
                        }
                        builder.addTypeAnnotations()
                        _typeBuilder = builder
                        try {
                            addDoc()
                            addMembers()
                            addType(builder.build())
                        } finally {
                            _typeBuilder = null
                        }
                    }.build()
                val writer = OutputStreamWriter(it, Charsets.UTF_8)
                fileSpec.writeTo(writer)
                writer.flush()
            }
        } else if (innerClassName !== null && parent !== null) {
            val builder = TypeSpec
                .classBuilder(innerClassName)
                .addModifiers(KModifier.OPEN)
                .addAnnotation(generatedAnnotation())
            builder.addTypeAnnotations()
            _typeBuilder = builder
            try {
                addDoc()
                addMembers()
                parent.typeBuilder.addType(builder.build())
            } finally {
                _typeBuilder = null
            }
        }
    }

    private fun FileSpec.Builder.addImports() {
        val packages = sortedSetOf<String>().also {
            collectImports(dtoType, it)
        }
        for (pkg in packages) {
            addImport(pkg, "by")
        }
    }

    private fun collectImports(
        dtoType: DtoType<ImmutableType, ImmutableProp>,
        packages: SortedSet<String>
    ) {
        packages += dtoType.baseType.className.packageName
        for (prop in dtoType.dtoProps) {
            val targetType = prop.targetType
            if (targetType !== null && (!prop.isRecursive || targetType.isFocusedRecursion)) {
                collectImports(targetType, packages)
            } else {
                prop.baseProp.targetType?.className?.packageName?.let {
                    packages += it
                }
            }
        }
    }

    private fun TypeSpec.Builder.addTypeAnnotations() {
        for (anno in dtoType.baseType.classDeclaration.annotations) {
            if (isCopyableAnnotation(anno, dtoType.annotations)) {
                addAnnotation(anno.toAnnotationSpec())
            }
        }
        for (anno in dtoType.annotations) {
            addAnnotation(annotationOf(anno))
        }
    }

    private fun addDoc() {
        (document.value ?: dtoType.baseType.classDeclaration.docString)?.let {
            typeBuilder.addKdoc(it.replace("%", "%%"))
        }
    }

    private fun addMembers() {
        if (isBuilderRequired) {
            typeBuilder.addAnnotation(
                AnnotationSpec
                    .builder(JSON_DESERIALIZE_CLASS_NAME)
                    .addMember("builder = %T::class", getDtoClassName("Builder"))
                    .build()
            )
        }
        val isSpecification = dtoType.modifiers.contains(DtoModifier.SPECIFICATION)
        if (isImpl && dtoType.baseType.isEntity) {
            typeBuilder.addSuperinterface(
                when {
                    isSpecification ->
                        K_SPECIFICATION_CLASS_NAME

                    dtoType.modifiers.contains(DtoModifier.INPUT) ->
                        INPUT_CLASS_NAME

                    else ->
                        VIEW_CLASS_NAME
                }.parameterizedBy(
                    dtoType.baseType.className
                )
            )
        }
        for (typeRef in dtoType.superInterfaces) {
            typeBuilder.addSuperinterface(typeName(typeRef))
        }

        addPrimaryConstructor()
        if (!isSpecification) {
            addConverterConstructor()
        }

        for (prop in dtoType.dtoProps) {
            addProp(prop)
            addStateProp(prop)
        }
        for (prop in dtoType.userProps) {
            addProp(prop)
        }

        if (isSpecification) {
            addEntityType()
            addApplyTo()
        } else {
            addToEntity()
            addToEntityImpl()
        }

        for (prop in dtoType.dtoProps) {
            typeBuilder.addSpecificationConverter(prop)
        }

        typeBuilder.addCopy()
        typeBuilder.addHashCode()
        typeBuilder.addEquals()
        typeBuilder.addToString()

        if (!isSpecification) {
            typeBuilder.addType(
                TypeSpec
                    .companionObjectBuilder()
                    .addAnnotation(generatedAnnotation())
                    .apply {
                        addMetadata()
                        addMetadataFetcherImpl()
                        for (prop in dtoType.dtoProps) {
                            addAccessorField(prop)
                        }
                    }
                    .build()
            )
        }

        for (prop in dtoType.dtoProps) {
            val targetType = prop.targetType ?: continue
            if (!prop.isRecursive || targetType.isFocusedRecursion) {
                DtoGenerator(
                    ctx,
                    mutable,
                    targetType,
                    null,
                    this,
                    targetSimpleName(prop)
                ).generate(emptyList())
            }
        }

        if (isBuilderRequired) {
            InputBuilderGenerator(this).generate()
        }
    }

    private fun TypeSpec.Builder.addMetadata() {
        addProperty(
            PropertySpec
                .builder(
                    "METADATA",
                    VIEW_METADATA_CLASS_NAME.parameterizedBy(
                        dtoType.baseType.className,
                        getDtoClassName()
                    )
                )
                .addAnnotation(JVM_STATIC_CLASS_NAME)
                .initializer(
                    CodeBlock
                        .builder()
                        .apply {
                            add("\n")
                            indent()
                            add(
                                "%T<%T, %T>(\n",
                                VIEW_METADATA_CLASS_NAME,
                                dtoType.baseType.className, getDtoClassName()
                            )
                            indent()
                            add("%M(%T::class).by(%T::fetcherImpl)",
                                NEW_FETCHER,
                                dtoType.baseType.className,
                                getDtoClassName()
                            )
                            unindent()
                            add(")")
                            beginControlFlow("")
                            addStatement("%T(it)", getDtoClassName())
                            endControlFlow()
                            unindent()
                        }
                        .build()
                )
                .build()
        )
    }

    private fun TypeSpec.Builder.addMetadataFetcherImpl() {
        addFunction(
            FunSpec
                .builder("fetcherImpl")
                .addKdoc(DOC_EXPLICIT_FUN)
                .addModifiers(KModifier.PRIVATE)
                .addAnnotation(JVM_STATIC_CLASS_NAME)
                .addParameter("_dsl", dtoType.baseType.fetcherDslClassName)
                .addCode(
                    CodeBlock
                        .builder()
                        .apply {
                            for (prop in dtoType.dtoProps) {
                                if (prop.nextProp === null) {
                                    addFetcherField(prop)
                                }
                            }
                            for (hiddenFlatProp in dtoType.hiddenFlatProps) {
                                addHiddenFetcherField(hiddenFlatProp)
                            }
                        }
                        .build()
                )
                .build()
        )
    }

    private fun CodeBlock.Builder.addFetcherField(prop: DtoProp<ImmutableType, ImmutableProp>) {
        if (!prop.baseProp.isId) {
            if (prop.targetType !== null) {
                if (prop.isRecursive) {
                    addStatement("_dsl.%N()", prop.baseProp.name + '*')
                } else {
                    addStatement(
                        "_dsl.%N(%T.METADATA.fetcher)",
                        prop.baseProp.name,
                        propElementName(prop)
                    )
                }
            } else {
                addStatement("_dsl.%N()", prop.baseProp.name)
            }
        }
    }

    private fun CodeBlock.Builder.addHiddenFetcherField(prop: DtoProp<ImmutableType, ImmutableProp>) {
        if ("flat" != prop.getFuncName()) {
            addFetcherField(prop)
            return
        }
        val targetDtoType = prop.getTargetType()!!
        beginControlFlow("_dsl.%N", prop.getBaseProp().name)
        for (childProp in targetDtoType.dtoProps) {
            addHiddenFetcherField(childProp)
        }
        endControlFlow()
    }

    private fun addStateProp(prop: DtoProp<ImmutableType, ImmutableProp>) {
        statePropName(prop, false)?.let {
            typeBuilder.addProperty(
                PropertySpec
                    .builder(it, BOOLEAN)
                    .addAnnotation(ApiIgnore::class)
                    .addAnnotation(
                        AnnotationSpec
                            .builder(JSON_IGNORE_CLASS_NAME)
                            .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                            .build()
                    )
                    .mutable(mutable)
                    .initializer(it)
                    .build()
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun addProp(prop: AbstractProp) {
        val typeName = propTypeName(prop)
        typeBuilder.addProperty(
            PropertySpec
                .builder(prop.name, typeName)
                .mutable(mutable)
                .apply {
                    if (interfacePropNames.contains(prop.name)) {
                        addModifiers(KModifier.OVERRIDE)
                    }
                    val doc = document[prop]
                        ?: prop.takeIf { it is DtoProp<*, *> && it.basePropMap.size == 1 && it.funcName === null }
                            ?.doc
                    doc?.let {
                        addKdoc(it.replace("%", "%%"))
                    }
                    if (!prop.isNullable) {
                        addAnnotation(
                            AnnotationSpec
                                .builder(JSON_PROPERTY_CLASS_NAME)
                                .addMember("required = true")
                                .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                                .build()
                        )
                    }
                    if (prop is DtoProp<*, *>) {
                        val dtoProp = prop as DtoProp<ImmutableType, ImmutableProp>
                        if (dtoType.modifiers.contains(DtoModifier.INPUT) && dtoProp.inputModifier == DtoModifier.FIXED) {
                            addAnnotation(FIXED_INPUT_FIELD_CLASS_NAME)
                        }
                        for (anno in dtoProp.baseProp.annotations {
                            isCopyableAnnotation(it, dtoProp.annotations)
                        }) {
                            useSiteTarget(anno.fullName)?.let {
                                addAnnotation(
                                    object : KSAnnotation by anno {
                                        override val useSiteTarget: AnnotationUseSiteTarget?
                                            get() = it
                                    }.toAnnotationSpec()
                                )
                            }
                        }
                    }
                    for (anno in prop.annotations) {
                        addAnnotation(
                            annotationOf(
                                anno,
                                when (useSiteTarget(anno.qualifiedName)){
                                    AnnotationUseSiteTarget.GET -> AnnotationSpec.UseSiteTarget.GET
                                    AnnotationUseSiteTarget.FIELD -> AnnotationSpec.UseSiteTarget.FIELD
                                    else -> AnnotationSpec.UseSiteTarget.PROPERTY
                                }
                            )
                        )
                    }
                    initializer(prop.name)
                    if (mutable) {
                        statePropName(prop, false)?.let { stateProp ->
                            val name = prop.name.takeIf { it != "field" } ?: "value"
                            setter(
                                FunSpec
                                    .setterBuilder()
                                    .addParameter(name, typeName)
                                    .addStatement("field = %L", name)
                                    .addStatement("%L = true", stateProp)
                                    .build()
                            )
                        }
                    }
                }
                .build()
        )
    }

    private fun addPrimaryConstructor() {
        typeBuilder.primaryConstructor(
            FunSpec
                .constructorBuilder()
                .apply {
                    for (prop in dtoType.dtoProps) {
                        addParameter(
                            ParameterSpec.builder(prop.name, propTypeName(prop))
                                .apply {
                                    if (prop.isNullable) {
                                        "null"
                                    } else {
                                        val typeName = propTypeName(prop)
                                        when {
                                            typeName is ParameterizedTypeName && typeName.rawType == LIST ->
                                                "emptyList()"
                                            typeName == BOOLEAN -> "false"
                                            typeName == CHAR -> "'\\0'"
                                            typeName == BYTE || typeName == SHORT || typeName == INT || typeName == LONG ->
                                                "0"
                                            typeName == FLOAT -> "0.0F"
                                            typeName == DOUBLE -> "0.0"
                                            else -> null
                                        }
                                    }?.let {
                                        defaultValue(it)
                                    }
                                }
                                .build()
                        )
                        statePropName(prop, false)?.let {
                            addParameter(
                                ParameterSpec
                                    .builder(
                                        StringUtil.identifier("is", prop.name, "Loaded"),
                                        BOOLEAN
                                    )
                                    .apply {
                                        if (prop.isNullable) {
                                            defaultValue("%L !== null", prop.name)
                                        } else {
                                            defaultValue("true")
                                        }
                                    }
                                    .build()
                            )
                        }
                    }
                    for (prop in dtoType.userProps) {
                        addParameter(
                            ParameterSpec.builder(prop.name, typeName(prop.typeRef))
                                .apply {
                                    defaultValue(prop)?.let {
                                        defaultValue(it)
                                    }
                                }
                                .build()
                        )
                    }
                }
                .build()
        )
    }

    private fun addConverterConstructor() {
        typeBuilder.addFunction(
            FunSpec
                .constructorBuilder()
                .addParameter("base", dtoType.baseType.className)
                .apply {
                    for (userProp in dtoType.userProps) {
                        addParameter(
                            ParameterSpec
                                .builder(userProp.alias, typeName(userProp.typeRef))
                                .apply {
                                    defaultValue(userProp)?.let {
                                        defaultValue(it)
                                    }
                                }
                                .build()
                        )
                    }
                }
                .callThisConstructor(dtoType.props.map { prop ->
                    CodeBlock
                        .builder()
                        .indent()
                        .add("\n")
                        .apply {
                            if (prop is DtoProp<*, *>) {
                                if (isSimpleProp(prop as DtoProp<ImmutableType, ImmutableProp>)) {
                                    add("base.%L", prop.baseProp.name)
                                } else if (!prop.isNullable && prop.isBaseNullable) {
                                    add(
                                        "%L.get<%T>(\n",
                                        StringUtil.snake("${prop.name}Accessor", SnakeCase.UPPER),
                                        propTypeName(prop)
                                    )
                                    indent()
                                    add("base,\n")
                                    add(
                                        "%S\n",
                                        "Cannot convert \"${dtoType.baseType.className}\" to " +
                                            "\"${getDtoClassName()}\" because the cannot get non-null " +
                                            "value for \"${prop.name}\""
                                    )
                                    unindent()
                                    add(")")
                                } else {
                                    add(
                                        "%L.get<%T>(base)",
                                        StringUtil.snake("${prop.name}Accessor", SnakeCase.UPPER),
                                        propTypeName(prop)
                                    )
                                }
                                statePropName(prop, false)?.let {
                                    if (isSimpleProp(prop as DtoProp<ImmutableType, ImmutableProp>)) {
                                        add(
                                            ",\n%T.%L.isLoaded(base)",
                                            dtoType.baseType.propsClassName,
                                            StringUtil.snake(prop.baseProp.name, SnakeCase.UPPER)
                                        )
                                    } else {
                                        add(
                                            ",\n%L.isLoaded(base)\n",
                                            StringUtil.snake("${prop.name}Accessor", SnakeCase.UPPER)
                                        )
                                    }
                                }
                            } else {
                                add("%N", prop.alias)
                            }
                        }
                        .unindent()
                        .build()
                })
                .build()
        )
    }

    private fun addToEntity() {
        typeBuilder.addFunction(
            FunSpec
                .builder(if (dtoType.baseType.isEntity) "toEntity" else "toImmutable")
                .apply {
                    if (dtoType.baseType.isEntity) {
                        addModifiers(KModifier.OVERRIDE)
                    }
                }
                .returns(dtoType.baseType.className)
                .addStatement(
                    "return %M(%T::class).by(null, this::%L)",
                    NEW,
                    dtoType.baseType.className,
                    if (dtoType.baseType.isEntity) "toEntityImpl" else "toImmutableImpl"
                )
                .build()
        )
    }

    private fun addToEntityImpl() {
        typeBuilder.addFunction(
            FunSpec
                .builder(if (dtoType.baseType.isEntity) "toEntityImpl" else "toImmutableImpl")
                .addKdoc(DOC_EXPLICIT_FUN)
                .addModifiers(KModifier.PRIVATE)
                .addParameter("_draft", dtoType.baseType.draftClassName)
                .apply {
                    for (prop in dtoType.dtoProps) {
                        val baseProp = prop.toTailProp().baseProp
                        if (baseProp.isKotlinFormula) {
                            continue
                        }
                        val statePropName = statePropName(prop, false)
                        if (statePropName !== null) {
                            beginControlFlow("if (%L)", statePropName)
                            addDraftAssignment(prop, prop.name)
                            endControlFlow()
                        } else {
                            addDraftAssignment(prop, prop.name)
                        }
                    }
                }
                .build()
        )
    }

    private fun FunSpec.Builder.addDraftAssignment(prop: DtoProp<ImmutableType, ImmutableProp>, valueExpr: String) {
        val baseProp = prop.toTailProp().baseProp
        if (isSimpleProp(prop)) {
            addStatement("_draft.%L = %L", baseProp.name, valueExpr)
        } else {
            if (prop.isNullable && baseProp.let { it.isList && it.isAssociation(true) }) {
                addStatement(
                    "%L.set(_draft, %L)",
                    StringUtil.snake("${prop.name}Accessor", SnakeCase.UPPER),
                    valueExpr
                )
            } else {
                addStatement(
                    "%L.set(_draft, %L)",
                    StringUtil.snake("${prop.name}Accessor", SnakeCase.UPPER),
                    valueExpr
                )
            }
        }
    }

    private fun addEntityType() {
        typeBuilder.addFunction(
            FunSpec
                .builder("entityType")
                .apply {
                    if (isImpl) {
                        addModifiers(KModifier.OVERRIDE)
                    }
                }
                .returns(
                    CLASS_CLASS_NAME.parameterizedBy(
                        dtoType.baseType.className
                    )
                )
                .addStatement("return %T::class.java", dtoType.baseType.className)
                .build()
        )
    }

    private fun addApplyTo() {
        typeBuilder.addFunction(
            FunSpec
                .builder("applyTo")
                .apply {
                    if (isImpl) {
                        addParameter(
                            "args",
                            K_SPECIFICATION_ARGS_CLASS_NAME.parameterizedBy(dtoType.baseType.className)
                        )
                        addModifiers(KModifier.OVERRIDE)
                        addStatement("val _applier = args.applier")
                    } else {
                        addParameter(
                            "_applier",
                            PREDICATE_APPLIER
                        )
                    }
                    var stack = emptyList<ImmutableProp>()
                    for (prop in dtoType.dtoProps) {
                        val newStack = mutableListOf<ImmutableProp>()
                        val tailProp = prop.toTailProp()
                        var p: DtoProp<ImmutableType, ImmutableProp>? = prop
                        while (p != null) {
                            if (p !== tailProp || p.getTargetType() != null) {
                                newStack.add(p.getBaseProp())
                            }
                            p = p.getNextProp()
                        }
                        stack = addStackOperations(stack, newStack)
                        addPredicateOperation(prop.toTailProp())
                    }
                    addStackOperations(stack, emptyList())
                }
                .build()
        )
    }

    private fun FunSpec.Builder.addStackOperations(
        stack: List<ImmutableProp>,
        newStack: List<ImmutableProp>
    ): List<ImmutableProp> {
        val size = min(stack.size, newStack.size)
        var sameCount = size
        for (i in 0 until size) {
            if (stack[i] !== newStack[i]) {
                sameCount = i
                break
            }
        }
        for (i in stack.size - sameCount downTo 1) {
            addStatement("_applier.pop()")
        }
        for (prop in newStack.subList(sameCount, newStack.size)) {
            addStatement(
                "_applier.push(%T.%L.unwrap())",
                prop.declaringType.propsClassName,
                StringUtil.snake(prop.name, SnakeCase.UPPER)
            )
        }
        return newStack
    }

    private fun FunSpec.Builder.addPredicateOperation(prop: DtoProp<ImmutableType, ImmutableProp>) {

        val targetType = prop.targetType
        if (targetType !== null) {
            if (targetType.baseType.isEntity) {
                addStatement("this.%L?.let { it.applyTo(args.child()) }", prop.name)
            } else {
                addStatement("this.%L?.let { it.applyTo(args.applier) }", prop.name)
            }
            return
        }

        val funcName = when (prop.funcName) {
            null -> "eq"
            "id" -> "associatedIdEq"
            else -> prop.funcName
        }
        val ktFunName = when (funcName) {
            "null" -> "isNull"
            "notNull" -> "isNotNull"
            else -> funcName
        }

        addCode(
            CodeBlock.builder()
                .apply {
                    add("_applier.%L(", ktFunName)
                    if (Constants.MULTI_ARGS_FUNC_NAMES.contains(funcName)) {
                        add("arrayOf(")
                        prop.basePropMap.values.forEachIndexed { index, baseProp ->
                            if (index != 0) {
                                add(", ")
                            }
                            add(
                                "%T.%L.unwrap()",
                                baseProp.declaringType.propsClassName,
                                StringUtil.snake(baseProp.name, SnakeCase.UPPER)
                            )
                        }
                        add(")")
                    } else {
                        add(
                            "%T.%L.unwrap()",
                            prop.baseProp.declaringType.propsClassName,
                            StringUtil.snake(prop.baseProp.name, SnakeCase.UPPER)
                        )
                    }
                    if (isSpecificationConverterRequired(prop)) {
                        add(
                            ", %L(this.%L)",
                            StringUtil.identifier("_convert", prop.name),
                            prop.name
                        )
                    } else {
                        add(", this.%L", prop.name)
                    }
                    if (funcName == "like") {
                        add(", ")
                        add(if (prop.likeOptions.contains(LikeOption.INSENSITIVE)) "true" else "false")
                        add(", ")
                        add(if (prop.likeOptions.contains(LikeOption.MATCH_START)) "true" else "false")
                        add(", ")
                        add(if (prop.likeOptions.contains(LikeOption.MATCH_END)) "true" else "false")
                    }
                    add(")\n")
                }
                .build()
        )
    }

    private fun isSimpleProp(prop: DtoProp<ImmutableType, ImmutableProp>): Boolean {
        if (prop.getNextProp() != null) {
            return false
        }
        return if (prop.isNullable() && (!prop.getBaseProp().isNullable ||
                dtoType.modifiers.contains(DtoModifier.SPECIFICATION))) {
            false
        } else {
            propTypeName(prop) == prop.getBaseProp().typeName()
        }
    }

    private fun TypeSpec.Builder.addAccessorField(prop: DtoProp<ImmutableType, ImmutableProp>) {
        if (isSimpleProp(prop)) {
            return
        }

        val builder = PropertySpec.builder(
            StringUtil.snake("${prop.name}Accessor", SnakeCase.UPPER),
            DTO_PROP_ACCESSOR,
            KModifier.PRIVATE
        ).initializer(
            CodeBlock
                .builder()
                .apply {
                    add("%T(", DTO_PROP_ACCESSOR)
                    indent()

                    if (prop.isNullable() && (!prop.toTailProp().getBaseProp().isNullable ||
                            dtoType.modifiers.contains(DtoModifier.SPECIFICATION) ||
                            dtoType.modifiers.contains(DtoModifier.FUZZY))
                    ) {
                        add("\nfalse")
                    } else {
                        add("\ntrue")
                    }

                    if (prop.nextProp === null) {
                        add(
                            ",\nintArrayOf(%T.%L)",
                            dtoType.baseType.draftClassName("$"),
                            prop.baseProp.slotName
                        )
                    } else {
                        add(",\nintArrayOf(")
                        indent()
                        var p: DtoProp<ImmutableType, ImmutableProp>? = prop
                        while (p !== null) {
                            if (p !== prop) {
                                add(",")
                            }
                            add(
                                "\n%T.%L",
                                p.baseProp.declaringType.draftClassName("$"),
                                p.baseProp.slotName
                            )
                            p = p.nextProp
                        }
                        unindent()
                        add("\n)")
                    }

                    val tailProp = prop.toTailProp()
                    val tailBaseProp = tailProp.baseProp
                    if (prop.isIdOnly) {
                        if (dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
                            add(",\nnull")
                        } else {
                            add(
                                ",\n%T.%L(%T::class.java, ",
                                DTO_PROP_ACCESSOR,
                                if (tailBaseProp.isList) "idListGetter" else "idReferenceGetter",
                                tailBaseProp.targetTypeName(overrideNullable = false)
                            )
                            addConverterLoading(prop, false)
                            add(")")
                            add(
                                ",\n%T.%L(%T::class.java, ",
                                DTO_PROP_ACCESSOR,
                                if (tailBaseProp.isList) "idListSetter" else "idReferenceSetter",
                                tailBaseProp.targetTypeName(overrideNullable = false)
                            )
                            addConverterLoading(prop, false)
                            add(")")
                        }
                    } else if (tailProp.targetType != null) {
                        if (dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
                            add(",\nnull")
                        } else {
                            add(
                                ",\n%T.%L<%T, %L> {",
                                DTO_PROP_ACCESSOR,
                                if (tailBaseProp.isList) "objectListGetter" else "objectReferenceGetter",
                                tailBaseProp.targetTypeName(overrideNullable = false),
                                propElementName(prop)
                            )
                            indent()
                            add("\n%L(it)", propElementName(prop))
                            unindent()
                            add("\n}")

                            add(
                                ",\n%T.%L<%T, %L> {",
                                DTO_PROP_ACCESSOR,
                                if (tailBaseProp.isList) "objectListSetter" else "objectReferenceSetter",
                                tailBaseProp.targetTypeName(overrideNullable = false),
                                propElementName(prop)
                            )
                            indent()
                            add(
                                "\nit.%L()",
                                if (tailBaseProp.targetType!!.isEntity) "toEntity" else "toImmutable"
                            )
                            unindent()
                            add("\n}")
                        }
                    } else if (prop.enumType !== null) {
                        val enumType = prop.enumType!!
                        val enumTypeName = tailBaseProp.targetTypeName(overrideNullable = false)
                        if (dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
                            add(",\nnull")
                        } else {
                            add(",\n{\n")
                            indent()
                            beginControlFlow("when (it as %T)", enumTypeName)
                            for ((en, v) in enumType.valueMap) {
                                addStatement("%T.%L -> %L", enumTypeName, en, v)
                            }
                            endControlFlow()
                            unindent()
                            add("}")
                        }
                        add(",\n{\n")
                        indent()
                        addValueToEnum(prop)
                        unindent()
                        add("}")
                    } else if (prop.dtoConverterMetadata != null) {
                        add(",\n{ ")
                        addConverterLoading(prop, true)
                        add(".output(it) }")
                        add(",\n{ ")
                        addConverterLoading(prop, true)
                        add(".input(it) }")
                    }

                    unindent()
                    add("\n)")
                }
                .build()
        )
        addProperty(builder.build())
    }

    private fun TypeSpec.Builder.addSpecificationConverter(prop: DtoProp<ImmutableType, ImmutableProp>) {
        if (!isSpecificationConverterRequired(prop)) {
            return
        }
        val baseProp = prop.toTailProp().getBaseProp()
        val baseTypeName = when (prop.funcName) {
            "id" -> baseProp.targetType!!.idProp!!.typeName().let {
                if (baseProp.isList && !dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
                    LIST.parameterizedBy(it)
                } else {
                    it
                }
            }
            "valueIn", "valueNotIn" ->
                LIST.parameterizedBy(baseProp.typeName())
            "associatedIdEq", "associatedIdNe" ->
                baseProp.targetType!!.idProp!!.typeName()
            "associatedIdIn", "associatedIdNotIn" ->
                LIST.parameterizedBy(baseProp.targetType!!.idProp!!.typeName())
            else -> baseProp.typeName()
        }.copy(nullable = prop.isNullable)
        val builder = FunSpec
            .builder(StringUtil.identifier("_convert", prop.getName()))
            .addModifiers(KModifier.PUBLIC)
            .addParameter("value", propTypeName(prop))
            .returns(baseTypeName)
            .addCode(
                CodeBlock
                    .builder()
                    .apply {
                        if (prop.isNullable) {
                            beginControlFlow("if (value === null)")
                            addStatement("return null")
                            endControlFlow()
                        }
                        if (prop.enumType !== null) {
                            add("return ")
                            addValueToEnum(prop, "value")
                        } else {
                            add(
                                "return %T.%L.unwrap().%L<%T, %T>(%L).input(value)",
                                dtoType.baseType.propsClassName,
                                StringUtil.snake(baseProp.name, SnakeCase.UPPER),
                                if (baseProp.isAssociation(true)) "getAssociatedIdConverter" else "getConverter",
                                baseTypeName,
                                propTypeName(prop).copy(nullable = false),
                                if (baseProp.isAssociation(true) || prop.isFunc("valueIn", "valueNotIn")) "true" else ""
                            )
                        }
                    }
                    .build()
            )
        addFunction(builder.build())
    }

    @Suppress("UNCHECKED_CAST")
    fun propTypeName(prop: AbstractProp): TypeName =
        when (prop) {
            is DtoProp<*, *> -> propTypeName(prop as DtoProp<ImmutableType, ImmutableProp>)
            is UserProp -> typeName(prop.typeRef)
            else -> error("Internal bug")
        }

    private fun propTypeName(prop: DtoProp<ImmutableType, ImmutableProp>): TypeName {

        val baseProp = prop.toTailProp().baseProp
        val enumType = prop.enumType
        if (enumType !== null) {
            return (if (enumType.isNumeric) INT else STRING).copy(nullable = prop.isNullable)
        }

        val metadata = prop.dtoConverterMetadata
        if (dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
            val funcName = prop.toTailProp().getFuncName()
            if (funcName != null) {
                when (funcName) {
                    "null", "notNull" ->
                        return BOOLEAN.copy(nullable = prop.isNullable)
                    "valueIn", "valueNotIn" ->
                        return COLLECTION.parameterizedBy(
                            metadata?.targetTypeName ?:
                            propElementName(prop).toList(baseProp.isList)
                        ).copy(nullable = prop.isNullable)
                    "id", "associatedIdEq", "associatedIdNe" ->
                        return baseProp.targetType!!.idProp!!.clientClassName.copy(nullable = prop.isNullable)
                    "associatedIdIn", "associatedIdNotIn" ->
                        return COLLECTION.parameterizedBy(baseProp.targetType!!.idProp!!.clientClassName)
                            .copy(nullable = prop.isNullable)
                }
            }
            if (baseProp.isAssociation(true)) {
                return propElementName(prop).copy(nullable = prop.isNullable)
            }
        }
        if (metadata != null) {
            return metadata.targetTypeName.copy(nullable = prop.isNullable)
        }

        return propElementName(prop).toList(baseProp.isList).copy(nullable = prop.isNullable)
    }

    private fun propElementName(prop: DtoProp<ImmutableType, ImmutableProp>): TypeName {
        val tailProp = prop.toTailProp()
        val targetType = tailProp.targetType
        if (targetType !== null) {
            if (tailProp.isRecursive && !targetType.isFocusedRecursion) {
                return getDtoClassName()
            }
            if (targetType.name === null) {
                val list: MutableList<String> = ArrayList()
                collectNames(list)
                if (!prop.isRecursive || targetType.isFocusedRecursion) {
                    list.add(targetSimpleName(tailProp))
                }
                return ClassName(
                    root.dtoType.packageName,
                    list[0],
                    *list.subList(1, list.size).toTypedArray()
                )
            }
            return ClassName(
                root.dtoType.packageName,
                targetType.name!!
            )
        }
        val baseProp = tailProp.baseProp
        return if (tailProp.isIdOnly) {
            baseProp.targetType!!.idProp!!.clientClassName
        } else if (baseProp.idViewBaseProp !== null) {
            baseProp.idViewBaseProp!!.targetType!!.idProp!!.clientClassName
        } else {
            tailProp.baseProp.clientClassName
        }.copy(nullable = false)
    }

    private fun collectNames(list: MutableList<String>) {
        if (parent == null) {
            list.add(dtoType.name!!)
        } else if (innerClassName !== null){
            parent.collectNames(list)
            list.add(innerClassName)
        }
    }

    private fun targetSimpleName(prop: DtoProp<ImmutableType, ImmutableProp>): String {
        val targetType = prop.targetType ?: throw IllegalArgumentException("prop is not association")
        if (prop.isRecursive && !targetType.isFocusedRecursion) {
            return innerClassName ?: dtoType.name ?: error("Internal bug: No target simple name")
        }
        return standardTargetSimpleName("TargetOf_${prop.name}")
    }

    private fun standardTargetSimpleName(targetSimpleName: String): String {
        var conflict = false
        var generator: DtoGenerator? = this
        while (generator != null) {
            if ((generator.innerClassName ?: generator.dtoType.name) == targetSimpleName) {
                conflict = true
                break
            }
            generator = generator.parent
        }
        if (!conflict) {
            return targetSimpleName
        }
        for (i in 2..99) {
            conflict = false
            val newTargetSimpleName = targetSimpleName + '_' + i
            generator = this
            while (generator != null) {
                if ((generator.innerClassName ?: generator.dtoType.name) == newTargetSimpleName) {
                    conflict = true
                    break
                }
                generator = generator.parent
            }
            if (!conflict) {
                return newTargetSimpleName
            }
        }
        throw AssertionError("Dto is too deep")
    }

    private fun CodeBlock.Builder.addValueToEnum(prop: DtoProp<ImmutableType, ImmutableProp>, variableName: String = "it") {
        beginControlFlow(
            "when ($variableName as %T)",
            if (propTypeName(prop).copy(nullable = false) == INT) INT else STRING
        )
        val enumTypeName = prop.toTailProp().baseProp.typeName(overrideNullable = false)
        for ((v, en) in prop.enumType!!.constantMap) {
            addStatement("%L -> %T.%L", v, enumTypeName, en)
        }
        addStatement("else -> throw IllegalArgumentException(")
        indent()
        addStatement("%S + $variableName + %S", "Illegal value \"", "\" for the enum type \"$enumTypeName\"")
        unindent()
        add(")\n")
        endControlFlow()
    }

    private fun CodeBlock.Builder.addConverterLoading(
        prop: DtoProp<ImmutableType, ImmutableProp>,
        forList: Boolean
    ) {
        val baseProp: ImmutableProp = prop.toTailProp().getBaseProp()
        add(
            "%T.%L.unwrap().%L",
            dtoType.baseType.propsClassName,
            StringUtil.snake(baseProp.name, SnakeCase.UPPER),
            if (prop.toTailProp().getBaseProp()
                    .isAssociation(true)
            ) {
                "getAssociatedIdConverter<Any, Any>($forList)"
            } else {
                "getConverter<Any, Any>()"
            }
        )
    }

    private fun isSpecificationConverterRequired(prop: DtoProp<ImmutableType, ImmutableProp>): Boolean {
        return if (!dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
            false
        } else {
            prop.getEnumType() != null || prop.dtoConverterMetadata != null
        }
    }

    private val DtoProp<ImmutableType, ImmutableProp>.dtoConverterMetadata: ConverterMetadata?
        get() {
            val baseProp = toTailProp().getBaseProp()
            val resolver = baseProp.ctx.resolver
            val metadata = baseProp.converterMetadata
            if (metadata != null) {
                return metadata
            }
            val funcName = getFuncName()
            if ("id" == funcName) {
                val metadata = baseProp.targetType!!.idProp!!.converterMetadata
                if (metadata != null && baseProp.isList && !dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
                    return metadata.toListMetadata(resolver)
                }
                return metadata
            }
            if ("associatedInEq" == funcName || "associatedInNe" == funcName) {
                return baseProp.targetType!!.idProp!!.converterMetadata
            }
            if ("associatedIdIn" == funcName || "associatedIdNotIn" == funcName) {
                return baseProp.targetType!!.idProp!!.converterMetadata?.toListMetadata(resolver)
            }
            if (baseProp.idViewBaseProp !== null) {
                return baseProp.idViewBaseProp!!.targetType!!.idProp!!.converterMetadata?.let {
                    if (baseProp.isList) it.toListMetadata(resolver) else it
                }
            }
            return null
        }

    private fun useSiteTarget(typeName: String): AnnotationUseSiteTarget? =
        useSiteTargetMap.computeIfAbsent(typeName) { tn ->
            val annotation = ctx.resolver.getClassDeclarationByName(tn)
                ?: error("Internal bug, cannot resolve annotation type \"$typeName\"")
            annotation.annotation(kotlin.annotation.Target::class)?.let {
                it
                    .get<List<KSType>>("allowedTargets")
                    ?.firstNotNullOf {
                        val s = it.toString()
                        when {
                            s.endsWith("FIELD") ->
                                AnnotationUseSiteTarget.FIELD
                            s.endsWith("PROPERTY") ->
                                AnnotationUseSiteTarget.PROPERTY
                            s.endsWith("PROPERTY_GETTER") ->
                                AnnotationUseSiteTarget.GET
                            s.endsWith("FUNCTION") ->
                                AnnotationUseSiteTarget.GET
                            else -> null
                        }
                    }
            }?: annotation.annotation(java.lang.annotation.Target::class)?.let {
                it
                    .get<List<KSType>>("value")
                    ?.firstNotNullOf {
                        val s = it.toString()
                        when {
                            s.endsWith("FIELD") ->
                                AnnotationUseSiteTarget.FIELD
                            s.endsWith("METHOD") ->
                                AnnotationUseSiteTarget.GET
                            else -> null
                        }
                    }
            } ?: AnnotationUseSiteTarget.FILE
        }?.takeIf { it != AnnotationUseSiteTarget.FILE }

    private fun TypeSpec.Builder.addCopy() {
        addFunction(
            FunSpec
                .builder("copy")
                .returns(getDtoClassName())
                .apply {
                    val args = mutableListOf<String>()
                    for (dtoProp in dtoType.dtoProps) {
                        addParameter(
                            ParameterSpec.builder(dtoProp.name, propTypeName(dtoProp))
                                .defaultValue("this.${dtoProp.name}")
                                .build()
                        )
                        args += dtoProp.name
                        statePropName(dtoProp, false)?.let {
                            addParameter(
                                ParameterSpec.builder(it, BOOLEAN)
                                    .defaultValue("this.$it")
                                    .build()
                            )
                            args += it
                        }
                    }
                    for (userProp in dtoType.userProps) {
                        addParameter(
                            ParameterSpec.builder(userProp.alias, typeName(userProp.typeRef))
                                .defaultValue("this.${userProp.alias}")
                                .build()
                        )
                        args += userProp.alias
                    }
                    addStatement("return %T(%L)", getDtoClassName(), args.joinToString())
                }
                .build()
        )
    }

    private fun TypeSpec.Builder.addHashCode() {
        addFunction(
            FunSpec
                .builder("hashCode")
                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                .returns(INT)
                .addCode(
                    CodeBlock
                        .builder()
                        .apply {
                            dtoType.props.forEachIndexed { index, prop ->
                                addStatement(
                                    "%L %L",
                                    if (index == 0) "var _hash =" else "_hash = 31 * _hash +",
                                    if (prop.isNullable) "(${prop.alias}?.hashCode() ?: 0)" else "${prop.alias}.hashCode()"
                                )
                                statePropName(prop, false)?.let {
                                    addStatement("_hash = _hash * 31 + %L.hashCode()", it)
                                }
                            }
                            addStatement("return _hash")
                        }
                        .build()
                )
                .build()
        )
    }

    private fun TypeSpec.Builder.addEquals() {
        addFunction(
            FunSpec
                .builder("equals")
                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                .addParameter("o", ANY.copy(nullable = true))
                .returns(BOOLEAN)
                .addCode(
                    CodeBlock.builder()
                        .apply {
                            addStatement("val _other = o as? %T ?: return false", getDtoClassName())
                            dtoType.props.forEachIndexed { index, prop ->
                                if (index == 0) {
                                    add("return ")
                                }
                                val statePropName = statePropName(prop, false)
                                if (statePropName !== null) {
                                    add("%L == _other.%L && (\n", statePropName, statePropName)
                                    indent()
                                    add("!%L || ", statePropName)
                                }
                                add("%L == _other.%L", prop.alias, prop.alias)
                                if (statePropName !== null) {
                                    unindent()
                                    add("\n)")
                                }
                                if (index + 1 < dtoType.props.size) {
                                    add(" &&")
                                }
                                add("\n")
                            }
                        }
                        .build()
                )
                .build()
        )
    }

    private fun TypeSpec.Builder.addToString() {
        addFunction(
            FunSpec.builder("toString")
                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                .returns(STRING)
                .addCode(
                    CodeBlock
                        .builder()
                        .apply {
                            val hashCondProps = dtoType.modifiers.contains(DtoModifier.INPUT) &&
                                dtoType.dtoProps.any { statePropName(it, false) != null || it.inputModifier == DtoModifier.FUZZY }
                            if (hashCondProps) {
                                addStatement("val builder = StringBuilder()")
                                addStatement("var separator = \"\"")
                                addStatement("builder.append(%S).append('(')", simpleNamePart())
                                for (prop in dtoType.getDtoProps()) {
                                    val stateFieldName = statePropName(prop, false)
                                    if (stateFieldName != null) {
                                        beginControlFlow("if (%L)", stateFieldName)
                                    } else if (prop.getInputModifier() == DtoModifier.FUZZY) {
                                        beginControlFlow("if (%L != null)", prop.getName())
                                    }
                                    if (prop.getName() == "builder") {
                                        addStatement(
                                            "builder.append(separator).append(%S).append(this.%L)",
                                            prop.getName() + '=',
                                            prop.getName()
                                        )
                                        addStatement("separator = \", \"")
                                    } else {
                                        addStatement(
                                            "builder.append(separator).append(%S).append(%L)",
                                            prop.getName() + '=',
                                            prop.getName()
                                        )
                                        addStatement("separator = \", \"")
                                    }
                                    if (stateFieldName != null || prop.getInputModifier() == DtoModifier.FUZZY) {
                                        endControlFlow()
                                    }
                                }
                                for (prop in dtoType.getUserProps()) {
                                    if (prop.alias == "builder") {
                                        addStatement(
                                            "builder.append(separator).append(%S).append(this.%L)",
                                            prop.alias + '=',
                                            prop.alias
                                        )
                                    } else {
                                        addStatement(
                                            "builder.append(separator).append(%S).append(%L)",
                                            prop.alias + '=',
                                            prop.alias
                                        )
                                    }
                                    addStatement("separator = \", \"")
                                }
                                addStatement("builder.append(')')")
                                addStatement("return builder.toString()")
                            } else {
                                add("return %S +\n", simpleNamePart() + "(")
                                dtoType.props.forEachIndexed { index, prop ->
                                    add(
                                        "    %S + %L + \n",
                                        (if (index == 0) "" else ", ") + prop.name + '=',
                                        prop.name
                                    )
                                }
                                add("    %S\n", ")")
                            }
                        }
                        .build()
                )
                .build()
        )
    }

    private fun simpleNamePart(): String =
        (innerClassName ?: dtoType.name!!).let { name ->
            parent
                ?.let {  "${it.simpleNamePart()}.$name" }
                ?: name
        }

    private class Document(
        dtoType: DtoType<ImmutableType, ImmutableProp>
    ) {
        private val dtoTypeDoc: Doc?
        private val baseTypeDoc: Doc?

        init {
            dtoTypeDoc = Doc.parse(dtoType.doc)
            baseTypeDoc = Doc.parse(dtoType.baseType.classDeclaration.docString)
        }

        val value: String? by lazy {
            (dtoTypeDoc?.toString() ?: baseTypeDoc?.toString())?.let {
                it.replace("%", "%%")
            }
        }

        operator fun get(prop: AbstractProp): String? {
            return getImpl(prop)?.let {
                it.replace("%", "%%")
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun getImpl(prop: AbstractProp): String? {
            val baseProp = (prop as? DtoProp<*, ImmutableProp?>)?.getBaseProp()
            if (prop.doc !== null) {
                val doc = Doc.parse(prop.doc)
                if (doc != null) {
                    return doc.toString()
                }
            }
            if (dtoTypeDoc != null) {
                val name = prop.getAlias() ?: baseProp!!.name
                val doc = dtoTypeDoc.parameterValueMap[name]
                if (doc != null) {
                    return doc
                }
            }
            if (baseProp != null) {
                val doc = Doc.parse(baseProp.propDeclaration.docString)
                if (doc != null) {
                    return doc.toString()
                }
            }
            if (baseTypeDoc != null && baseProp != null) {
                val doc = baseTypeDoc.parameterValueMap[baseProp.name]
                if (doc != null) {
                    return doc
                }
            }
            return null
        }
    }

    private val isImpl: Boolean
        get() = dtoType.baseType.isEntity || !dtoType.modifiers.contains(DtoModifier.SPECIFICATION)

    internal fun statePropName(prop: AbstractProp, builder: Boolean): String? =
        when {
            !prop.isNullable -> null
            prop !is DtoProp<*, *> -> null
            !dtoType.modifiers.contains(DtoModifier.INPUT) -> null
            else -> prop.inputModifier?.takeIf {
                (it == DtoModifier.FIXED && builder) || it == DtoModifier.DYNAMIC
            }?.let {
                StringUtil.identifier("is", prop.name, "Loaded")
            }
        }

    private val isBuilderRequired: Boolean by lazy {
        dtoType.modifiers.contains(DtoModifier.INPUT) &&
            dtoType.dtoProps.any { prop ->
                prop.inputModifier.let { it == DtoModifier.FIXED || it == DtoModifier.DYNAMIC }
            }
    }

    companion object {

        @JvmStatic
        private val NEW = MemberName("org.babyfish.jimmer.kt", "new")

        @JvmStatic
        private val NEW_FETCHER = MemberName("org.babyfish.jimmer.sql.kt.fetcher", "newFetcher")

        private fun isCopyableAnnotation(annotation: KSAnnotation, dtoAnnotations: Collection<Anno>): Boolean {
            val qualifiedName = annotation.annotationType.resolve().declaration.qualifiedName!!.asString()
            return (
                (!qualifiedName.startsWith("org.babyfish.jimmer.") ||
                    qualifiedName.startsWith("org.babyfish.jimmer.client.")
                ) && dtoAnnotations.none {
                    it.qualifiedName == annotation.annotationType.resolve().declaration.qualifiedName?.asString()
                }
            )
        }

        private fun annotationOf(anno: Anno, target: AnnotationSpec.UseSiteTarget? = null): AnnotationSpec =
            AnnotationSpec
                .builder(ClassName.bestGuess(anno.qualifiedName))
                .apply {
                    if (anno.valueMap.isNotEmpty()) {
                        addMember(
                            CodeBlock
                                .builder()
                                .apply {
                                    if (anno.valueMap.let { it.size == 1 && it.keys.first() == "value" }) {
                                        add("(")
                                        add(anno.valueMap.values.first())
                                        add(")")
                                    } else {
                                        add("\n")
                                        add(anno.valueMap)
                                        add("\n")
                                    }
                                }
                                .build()
                        )
                    }
                    target?.let {
                        useSiteTarget(it)
                    }
                }
                .build()

        private fun CodeBlock.Builder.add(value: Value) {
            when (value) {
                is ArrayValue -> {
                    add("[\n")
                    indent()
                    var addSeparator = false
                    for (element in value.elements) {
                        if (addSeparator) {
                            add(", \n")
                        } else {
                            addSeparator = true
                        }
                        add(element)
                    }
                    unindent()
                    add("\n]")
                }
                is AnnoValue -> {
                    add("%T", ClassName.bestGuess(value.anno.qualifiedName))
                    if (value.anno.valueMap.isEmpty()) {
                        add("{}")
                    } else if (value.anno.valueMap.let { it.size == 1 && it.keys.first() == "value" }) {
                        add("(")
                        add(value.anno.valueMap.values.first())
                        add(")")
                    } else {
                        add("(\n")
                        add(value.anno.valueMap)
                        add("\n)")
                    }
                }
                is TypeRefValue -> value.typeRef.let {
                    if (it.isNullable) {
                        add(
                            "java.lang.%L::class",
                            when (it.typeName) {
                                "Char" -> "Character"
                                "Int" -> "Integer"
                                else -> it.typeName
                            }
                        )
                    } else {
                        add("%T::class", typeName(it))
                    }
                }
                is EnumValue -> add(
                    "%T.%N",
                    ClassName.bestGuess(value.qualifiedName),
                    value.constant
                )
                else -> add((value as LiteralValue).value)
            }
        }

        private fun CodeBlock.Builder.add(valueMap: Map<String, Value>) {
            indent()
            var addSeparator = false
            for ((name, value) in valueMap) {
                if (addSeparator) {
                    add(", \n")
                } else {
                    addSeparator = true
                }
                add("%N = ", name)
                add(value)
            }
            unindent()
        }

        fun typeName(typeRef: TypeRef?): TypeName {
            val typeName = if (typeRef === null) {
                STAR
            } else {
                when (typeRef.typeName) {
                    TypeRef.TN_BOOLEAN -> BOOLEAN
                    TypeRef.TN_CHAR -> CHAR
                    TypeRef.TN_BYTE -> BYTE
                    TypeRef.TN_SHORT -> SHORT
                    TypeRef.TN_INT -> INT
                    TypeRef.TN_LONG -> LONG
                    TypeRef.TN_FLOAT -> FLOAT
                    TypeRef.TN_DOUBLE -> DOUBLE
                    TypeRef.TN_ANY -> ANY
                    TypeRef.TN_STRING -> STRING
                    TypeRef.TN_ARRAY ->
                        if (typeRef.arguments[0].typeRef == null) {
                            ARRAY.parameterizedBy(STAR)
                        } else if (typeRef.arguments[0].typeRef?.isNullable == true) {
                            ARRAY.parameterizedBy(typeName(typeRef.arguments[0].typeRef))
                        } else {
                            val componentTypeRef = typeRef.arguments[0].typeRef
                            if (componentTypeRef == null) {
                                ARRAY.parameterizedBy(
                                    WildcardTypeName.producerOf(ANY)
                                )
                            } else {
                                when (componentTypeRef.typeName) {
                                    TypeRef.TN_BOOLEAN -> BOOLEAN_ARRAY
                                    TypeRef.TN_CHAR -> CHAR_ARRAY
                                    TypeRef.TN_BYTE -> BYTE_ARRAY
                                    TypeRef.TN_SHORT -> SHORT_ARRAY
                                    TypeRef.TN_INT -> INT_ARRAY
                                    TypeRef.TN_LONG -> LONG_ARRAY
                                    TypeRef.TN_FLOAT -> FLOAT_ARRAY
                                    TypeRef.TN_DOUBLE -> DOUBLE_ARRAY
                                    else -> ARRAY.parameterizedBy(typeName(typeRef.arguments[0].typeRef))
                                }
                            }
                        }

                    TypeRef.TN_ITERABLE -> ITERABLE
                    TypeRef.TN_MUTABLE_ITERABLE -> MUTABLE_ITERABLE
                    TypeRef.TN_COLLECTION -> COLLECTION
                    TypeRef.TN_MUTABLE_COLLECTION -> MUTABLE_COLLECTION
                    TypeRef.TN_LIST -> LIST
                    TypeRef.TN_MUTABLE_LIST -> MUTABLE_LIST
                    TypeRef.TN_SET -> SET
                    TypeRef.TN_MUTABLE_SET -> MUTABLE_SET
                    TypeRef.TN_MAP -> MAP
                    TypeRef.TN_MUTABLE_MAP -> MUTABLE_MAP
                    else -> ClassName.bestGuess(typeRef.typeName)
                }
            }
            val args = typeRef
                ?.arguments
                ?.takeIf { it.isNotEmpty() && typeRef.typeName != TypeRef.TN_ARRAY }
                ?.let { args ->
                    Array(args.size) { i ->
                        typeName(args[i].typeRef).let {
                            when {
                                args[i].isIn -> WildcardTypeName.consumerOf(it)
                                args[i].isOut -> WildcardTypeName.producerOf(it)
                                else -> it
                            }
                        }
                    }
                }
            return if (args == null) {
                typeName
            } else {
                (typeName as ClassName).parameterizedBy(*args)
            }.copy(
                nullable = typeRef?.isNullable ?: false
            )
        }

        private fun defaultValue(prop: UserProp): String? {
            val typeRef = prop.typeRef
            return if (typeRef.isNullable) {
                "null"
            } else {
                when (typeRef.typeName) {
                    TypeRef.TN_BOOLEAN -> "false"
                    TypeRef.TN_CHAR -> "'\\0'"

                    TypeRef.TN_BYTE, TypeRef.TN_SHORT, TypeRef.TN_INT, TypeRef.TN_LONG,
                    TypeRef.TN_FLOAT, TypeRef.TN_DOUBLE -> "0"

                    TypeRef.TN_STRING -> "\"\""

                    TypeRef.TN_ARRAY -> if (typeRef.arguments[0].typeRef == null) {
                        "emptyArray<Any?>()"
                    } else if (typeRef.arguments[0].typeRef?.isNullable == true) {
                        "emptyArray()"
                    } else {
                        val componentTypeRef = typeRef.arguments[0].typeRef
                        if (componentTypeRef === null) {
                            "emptyArray()"
                        } else {
                            when (componentTypeRef.typeName) {
                                TypeRef.TN_BOOLEAN -> "booleanArrayOf()"
                                TypeRef.TN_CHAR -> "charArrayOf()"
                                TypeRef.TN_BYTE -> "byteArrayOf()"
                                TypeRef.TN_SHORT -> "shortArrayOf()"
                                TypeRef.TN_INT -> "intArrayOf()"
                                TypeRef.TN_LONG -> "longArrayOf()"
                                TypeRef.TN_FLOAT -> "floatArrayOf()"
                                TypeRef.TN_DOUBLE -> "doubleArrayOf()"
                                else -> "emptyArray()"
                            }
                        }
                    }

                    TypeRef.TN_ITERABLE, TypeRef.TN_COLLECTION, TypeRef.TN_LIST ->
                        if (typeRef.arguments[0].typeRef === null) {
                            "emptyList<Any?>()"
                        } else {
                            "emptyList()"
                        }

                    TypeRef.TN_MUTABLE_ITERABLE, TypeRef.TN_MUTABLE_COLLECTION, TypeRef.TN_MUTABLE_LIST ->
                        if (typeRef.arguments[0].typeRef === null) {
                            "mutableListOf<Any?>()"
                        } else {
                            "mutableListOf()"
                        }

                    TypeRef.TN_SET -> "emptySet()"
                    TypeRef.TN_MUTABLE_SET -> "mutableSetOf()"

                    TypeRef.TN_MAP -> "emptyMap()"
                    TypeRef.TN_MUTABLE_MAP -> "mutableMapOf()"

                    else -> null
                }
            }
        }

        private fun String.simpleName() =
            lastIndexOf('.').let {
                if (it == -1) {
                    this
                } else {
                    substring(it + 1)
                }
            }

        private fun TypeName.toList(isList: Boolean) =
            if (isList) {
                LIST.parameterizedBy(this.copy(nullable = false))
            } else {
                this
            }

        val DOC_EXPLICIT_FUN = "Avoid anonymous lambda affects coverage of non-kotlin-friendly tools such as jacoco"
    }
}