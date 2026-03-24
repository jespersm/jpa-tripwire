package io.github.jespersm.twca.indexinator.core.requirement;

import io.github.jespersm.twca.indexinator.core.model.IssueSeverity;
import io.github.jespersm.twca.indexinator.core.model.IssueType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Normalized index requirement that can originate from multiple metadata sources.
 */
public class IndexRequirement {
	private final Class<?> entityClass;
	private final List<String> propertyPaths;
	private final boolean unique;
	private final RequirementSource source;
	private final IssueType issueType;
	private final IssueSeverity severity;
	private final String context;

	private IndexRequirement(Class<?> entityClass,
							 List<String> propertyPaths,
							 boolean unique,
							 RequirementSource source,
							 IssueType issueType,
							 IssueSeverity severity,
							 String context) {
		this.entityClass = entityClass;
		this.propertyPaths = new ArrayList<>(propertyPaths);
		this.unique = unique;
		this.source = source;
		this.issueType = issueType;
		this.severity = severity;
		this.context = context;
	}

	public static IndexRequirement forProperties(Class<?> entityClass,
												 List<String> propertyPaths,
												 boolean unique,
												 RequirementSource source,
												 IssueType issueType,
												 IssueSeverity severity,
												 String context) {
		return new IndexRequirement(entityClass, propertyPaths, unique, source, issueType, severity, context);
	}

	public Class<?> getEntityClass() {
		return entityClass;
	}

	public List<String> getPropertyPaths() {
		return Collections.unmodifiableList(propertyPaths);
	}


	public boolean isUnique() {
		return unique;
	}

	public RequirementSource getSource() {
		return source;
	}

	public IssueType getIssueType() {
		return issueType;
	}

	public IssueSeverity getSeverity() {
		return severity;
	}

	public String getContext() {
		return context;
	}
}

