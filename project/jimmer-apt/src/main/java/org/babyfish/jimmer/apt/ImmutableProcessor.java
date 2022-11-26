package org.babyfish.jimmer.apt;

import org.babyfish.jimmer.apt.generator.*;
import org.babyfish.jimmer.apt.meta.ImmutableType;
import org.babyfish.jimmer.apt.meta.MetaException;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({
        "org.babyfish.jimmer.Immutable",
        "org.babyfish.jimmer.sql.Entity",
        "org.babyfish.jimmer.sql.MappedSuperclass"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ImmutableProcessor extends AbstractProcessor {

    private TypeUtils typeUtils;

    private Filer filer;

    private String[] includes = null;

    private String[] excludes = null;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        String includes = processingEnv.getOptions().get("jimmer.source.includes");
        String excludes = processingEnv.getOptions().get("jimmer.source.excludes");
        if (includes != null && !includes.isEmpty()) {
            this.includes = includes.trim().split("\\s*,\\s*");
        }
        if (excludes != null && !excludes.isEmpty()) {
            this.excludes = excludes.trim().split("\\s*,\\s*");
        }
        typeUtils = new TypeUtils(
                processingEnv.getElementUtils(),
                processingEnv.getTypeUtils()
        );
        filer = processingEnv.getFiler();
    }

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv
    ) {
        PackageCollector packageCollector = new PackageCollector();
        for (Element element : roundEnv.getRootElements()) {
            if (element instanceof TypeElement) {
                TypeElement typeElement = (TypeElement)element;
                String qualifiedName = typeElement.getQualifiedName().toString();
                if (includes != null) {
                    boolean matched = false;
                    for (String include : includes) {
                        if (qualifiedName.startsWith(include)) {
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) {
                        continue;
                    }
                }
                if (excludes != null) {
                    boolean matched = false;
                    for (String exclude : excludes) {
                        if (qualifiedName.startsWith(exclude)) {
                            matched = true;
                            break;
                        }
                    }
                    if (matched) {
                        continue;
                    }
                }
                if (typeUtils.isImmutable(typeElement)) {
                    if (typeElement.getKind() != ElementKind.INTERFACE) {
                        throw new MetaException(
                                "Illegal class \"" +
                                        qualifiedName +
                                        "\", immutable type must be interface"
                        );
                    }
                    ImmutableType immutableType = typeUtils.getImmutableType(typeElement);
                    new DraftGenerator(
                            typeUtils,
                            immutableType,
                            filer
                    ).generate();
                    new PropsGenerator(
                            typeUtils,
                            immutableType,
                            filer
                    ).generate();
                    if (immutableType.isEntity()) {
                        packageCollector.accept(typeElement);
                        new TableGenerator(
                                typeUtils,
                                immutableType,
                                false,
                                filer
                        ).generate();
                        new TableGenerator(
                                typeUtils,
                                immutableType,
                                true,
                                filer
                        ).generate();
                        new FetcherGenerator(
                                typeUtils,
                                immutableType,
                                filer
                        ).generate();
                    }
                }
            }
        }
        new EntityManagersGenerator(
                packageCollector.toString(),
                packageCollector.getTypeElements(),
                filer
        ).generate();
        return true;
    }

    private static class PackageCollector {

        private static final Pattern DOT_PATTERN = Pattern.compile("\\.");

        private List<String> paths;

        private String str;

        private List<TypeElement> typeElements = new ArrayList<>();

        public void accept(TypeElement typeElement) {
            typeElements.add(typeElement);
            if (paths != null && paths.isEmpty()) {
                return;
            }
            str = null;
            List<String> newPaths = Collections.emptyList();
            for (Element parent = typeElement.getEnclosingElement(); parent != null; parent = parent.getEnclosingElement()) {
                if (parent instanceof PackageElement) {
                    String packageName = ((PackageElement) parent).getQualifiedName().toString();
                    newPaths = Arrays.asList(DOT_PATTERN.split(packageName));
                    break;
                }
            }
            if (paths == null) {
                paths = newPaths;
            } else {
                int len = Math.min(paths.size(), newPaths.size());
                int index = 0;
                while (index < len) {
                    if (!paths.get(index).equals(newPaths.get(index))) {
                        break;
                    }
                    index++;
                }
                if (index < paths.size()) {
                    paths.subList(index, paths.size() - index).clear();
                }
            }
        }

        public List<TypeElement> getTypeElements() {
            return Collections.unmodifiableList(typeElements);
        }

        @Override
        public String toString() {
            String s = str;
            if (s == null) {
                List<String> ps = paths;
                str = s = ps == null || ps.isEmpty() ? "" : String.join(".", ps);
            }
            return s;
        }
    }
}
