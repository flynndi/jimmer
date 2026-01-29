
plugins {
    `kotlin-convention`
    alias(libs.plugins.buildconfig)
}

dependencies {
    api(libs.jspecify)
    api(libs.kotlin.reflect)
    implementation(libs.javax.validation.api)
    implementation(libs.kotlin.stdlib)
    compileOnly(libs.mapstruct)
    compileOnly(libs.bundles.jackson)

    testImplementation(libs.bundles.jackson)
    testCompileOnly(libs.mapstruct)
    testCompileOnly(libs.lombok)

    testAnnotationProcessor(projects.jimmerApt)
    testAnnotationProcessor(libs.lombok)
    testAnnotationProcessor(libs.mapstruct.processor)
    testAnnotationProcessor(libs.bundles.jackson)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Ajimmer.source.excludes=org.babyfish.jimmer.invalid")
    options.compilerArgs.add("-Ajimmer.generate.dynamic.pojo=true")
}

buildConfig {
    val versionParts = (project.version as String).split('.')
    packageName(project.group as String)
    className("JimmerVersion")
    buildConfigField("int", "major", versionParts[0])
    buildConfigField("int", "minor", versionParts[1])
    buildConfigField("int", "patch", versionParts[2])
    useKotlinOutput {
        internalVisibility = false
    }
}