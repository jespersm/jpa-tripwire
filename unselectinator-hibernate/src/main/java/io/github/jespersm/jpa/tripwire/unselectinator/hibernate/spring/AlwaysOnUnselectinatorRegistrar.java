package io.github.jespersm.jpa.tripwire.unselectinator.hibernate.spring;

import io.github.jespersm.jpa.tripwire.unselectinator.hibernate.CollectionLoadGuard;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

import java.util.List;

final class AlwaysOnUnselectinatorRegistrar implements ImportBeanDefinitionRegistrar {
    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        var attributes = metadata.getAnnotationAttributes(EnableUnselectinatorAlwaysOn.class.getName());
        var configuration = new CollectionLoadGuard.Configuration(null, null,
                (double) attributes.get("ratio"), (int) attributes.get("minimumGroupSize"),
                (int) attributes.get("minimumLazyLoads"), List.of((Class<?>[]) attributes.get("includes")),
                List.of((Class<?>[]) attributes.get("excludes")));
        var definition = BeanDefinitionBuilder.genericBeanDefinition(CollectionLoadGuard.class,
                () -> new CollectionLoadGuard(configuration)).getBeanDefinition();
        definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        String name = "alwaysOnUnselectinatorGuard";
        if (registry.containsBeanDefinition(name)) {
            throw new IllegalStateException("Declare @EnableUnselectinatorAlwaysOn once per application context");
        }
        registry.registerBeanDefinition(name, definition);
    }
}
