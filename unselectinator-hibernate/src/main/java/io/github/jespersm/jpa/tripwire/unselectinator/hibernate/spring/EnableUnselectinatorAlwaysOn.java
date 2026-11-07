package io.github.jespersm.jpa.tripwire.unselectinator.hibernate.spring;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Enable automatic N+1 enforcement once for a Spring test application context. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import({UnselectinatorSpringConfiguration.class, AlwaysOnUnselectinatorRegistrar.class})
public @interface EnableUnselectinatorAlwaysOn {
    /** Exclusive upper bound for SQL-producing lazy operations / distinct group members. */
    double ratio() default 0.5;
    /** Ignore source groups smaller than this number of distinct entities. */
    int minimumGroupSize() default 5;
    /** Require this many SQL-producing lazy operations before enforcing the ratio. */
    int minimumLazyLoads() default 3;
    /** Association owner types to check, including subclasses; empty means all types. */
    Class<?>[] includes() default {};
    /** Association owner types to skip, including subclasses; takes precedence over includes. */
    Class<?>[] excludes() default {};
}
