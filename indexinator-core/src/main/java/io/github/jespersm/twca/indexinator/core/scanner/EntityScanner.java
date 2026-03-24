package io.github.jespersm.twca.indexinator.core.scanner;

import jakarta.persistence.Entity;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import java.util.HashSet;
import java.util.Set;

/**
 * Scans classpath for JPA entity classes
 */
public class EntityScanner {

    /**
     * Scan a package for all @Entity annotated classes
     *
     * @param basePackage the package to scan (e.g., "com.myapp.entity")
     * @return Set of entity classes found
     */
    public Set<Class<?>> scanForEntities(String basePackage) {
        if (basePackage == null || basePackage.trim().isEmpty()) {
            throw new IllegalArgumentException("Base package cannot be null or empty");
        }

        Reflections reflections = new Reflections(
                new ConfigurationBuilder()
                        .setUrls(ClasspathHelper.forPackage(basePackage))
                        .setScanners(Scanners.TypesAnnotated)
        );

        return new HashSet<>(reflections.getTypesAnnotatedWith(Entity.class));
    }

    /**
     * Scan multiple packages for entity classes
     *
     * @param basePackages packages to scan
     * @return Set of all entity classes found
     */
    public Set<Class<?>> scanForEntities(String... basePackages) {
        Set<Class<?>> allEntities = new HashSet<>();

        for (String basePackage : basePackages) {
            allEntities.addAll(scanForEntities(basePackage));
        }

        return allEntities;
    }
}
