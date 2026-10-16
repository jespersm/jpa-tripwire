package io.github.jespersm.jpa.tripwire.test.model.fixture;

import jakarta.persistence.metamodel.Metamodel;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataBuilder;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Environment;

/**
 * Test utility for building JPA metamodels from entity classes.
 * Uses Hibernate's bootstrap API to create metamodels in unit tests.
 */
public class MetamodelBuilder {

	/**
	 * Build a metamodel from the given entity classes.
	 * Uses an in-memory H2 database for the bootstrap process.
	 *
	 * @param entityClasses the entity classes to include in the metamodel
	 * @return the JPA Metamodel
	 */
	public static Metamodel buildMetamodel(Class<?>... entityClasses) {
		StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
				.applySetting(Environment.DRIVER, "org.h2.Driver")
				.applySetting(Environment.URL, "jdbc:h2:mem:test")
				.applySetting(Environment.USER, "sa")
				.applySetting(Environment.PASS, "")
				.applySetting(Environment.DIALECT, "org.hibernate.dialect.H2Dialect")
				.build();

		try {
			MetadataSources sources = new MetadataSources(registry);
			for (Class<?> entityClass : entityClasses) {
				sources.addAnnotatedClass(entityClass);
			}

			MetadataBuilder metadataBuilder = sources.getMetadataBuilder()
					.applyImplicitNamingStrategy(new org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl());

			SessionFactory sessionFactory = metadataBuilder
					.build()
					.buildSessionFactory();

			try {
				return sessionFactory.getMetamodel();
			} finally {
				sessionFactory.close();
			}
		} finally {
			StandardServiceRegistryBuilder.destroy(registry);
		}
	}
}
