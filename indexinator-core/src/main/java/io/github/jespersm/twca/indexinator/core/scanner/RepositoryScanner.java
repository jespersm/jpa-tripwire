package io.github.jespersm.twca.indexinator.core.scanner;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.data.repository.Repository;

import java.util.HashSet;
import java.util.Set;

/**
 * Scans classpath for Spring Data Repository interfaces
 */
public class RepositoryScanner {

    /**
     * Scan a package for all Repository interfaces
     *
     * @param basePackage the package to scan (e.g., "com.myapp.repository")
     * @return Set of repository interfaces found
     */
    public Set<Class<?>> scanForRepositories(String basePackage) {
        if (basePackage == null || basePackage.trim().isEmpty()) {
            throw new IllegalArgumentException("Base package cannot be null or empty");
        }

        Reflections reflections = new Reflections(
                new ConfigurationBuilder()
                        .setUrls(ClasspathHelper.forPackage(basePackage))
                        .setScanners(Scanners.SubTypes)
        );

        // Find all interfaces that extend Repository
        Set<Class<?>> repositories = new HashSet<>();
        Set<Class<? extends Repository>> foundRepos = reflections.getSubTypesOf(Repository.class);

        for (Class<? extends Repository> repo : foundRepos) {
            // Only include interfaces (not implementations)
            if (repo.isInterface()) {
                repositories.add(repo);
            }
        }

        return repositories;
    }

    /**
     * Scan multiple packages for repository interfaces
     *
     * @param basePackages packages to scan
     * @return Set of all repository interfaces found
     */
    public Set<Class<?>> scanForRepositories(String... basePackages) {
        Set<Class<?>> allRepositories = new HashSet<>();

        for (String basePackage : basePackages) {
            allRepositories.addAll(scanForRepositories(basePackage));
        }

        return allRepositories;
    }
}
