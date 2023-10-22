package org.babyfish.jimmer.spring.repository.support;

import org.babyfish.jimmer.spring.repository.JRepository;
import org.babyfish.jimmer.spring.repository.KRepository;
import org.babyfish.jimmer.spring.repository.bytecode.ClassCodeWriter;
import org.babyfish.jimmer.spring.repository.bytecode.JavaClassCodeWriter;
import org.babyfish.jimmer.spring.repository.bytecode.JavaClasses;
import org.babyfish.jimmer.spring.repository.bytecode.KotlinClassCodeWriter;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContext;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;

public class JimmerRepositoryFactory extends RepositoryFactorySupport {

    private final ApplicationContext ctx;

    private final String sqlClientRef;

    public JimmerRepositoryFactory(ApplicationContext ctx, String sqlClientRef) {
        this.ctx = ctx;
        this.sqlClientRef = sqlClientRef;
    }

    @NotNull
    @Override
    public <T, ID> EntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {
        return null;
    }

    @NotNull
    @Override
    protected Object getTargetRepository(RepositoryInformation metadata) {
        Class<?> repositoryInterface = metadata.getRepositoryInterface();
        boolean jRepository = JRepository.class.isAssignableFrom(repositoryInterface);
        boolean kRepository = KRepository.class.isAssignableFrom(repositoryInterface);
        if (jRepository && kRepository) {
            throw new IllegalStateException(
                    "Illegal repository interface \"" +
                            repositoryInterface.getName() +
                            "\", it can not extend both \"" +
                            JRepository.class.getName() +
                            "\" and \"" +
                            KRepository.class.getName() +
                            "\""
            );
        }
        Class<?> clazz = null;
        try {
            clazz = Class.forName(
                    ClassCodeWriter.implementationClassName(repositoryInterface),
                    true,
                    repositoryInterface.getClassLoader()
            );
        } catch (ClassNotFoundException ex) {
            // Do nothing
        }
        if (clazz == null) {
            ClassCodeWriter writer = jRepository ?
                    new JavaClassCodeWriter(metadata) :
                    new KotlinClassCodeWriter(metadata);
            byte[] bytecode = writer.write();
            clazz = JavaClasses.define(bytecode, repositoryInterface);
        }
        try {
            return clazz.getConstructor(ApplicationContext.class, String.class).newInstance(ctx, sqlClientRef);
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException ex) {
            throw new AssertionError("Internal bug", ex);
        } catch (InvocationTargetException ex) {
            Throwable targetException = ex.getTargetException();
            if (targetException instanceof RuntimeException) {
                throw (RuntimeException) targetException;
            }
            if (targetException instanceof Error) {
                throw (Error) targetException;
            }
            throw new UndeclaredThrowableException(ex.getTargetException(), "Failed to create repository");
        }
    }

    @NotNull
    @Override
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
        return metadata.getRepositoryInterface();
    }
}
