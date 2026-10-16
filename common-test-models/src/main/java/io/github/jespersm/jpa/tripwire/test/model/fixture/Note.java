package io.github.jespersm.jpa.tripwire.test.model.fixture;

import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;

/**
 * Embedded note value object.
 * Demonstrates @Embeddable used in OrderComment alongside ElementCollection.
 */
@Embeddable
public class Note {
	private String text;
	private LocalDateTime createdAt;

	protected Note() {
	}

	public Note(String text, LocalDateTime createdAt) {
		this.text = text;
		this.createdAt = createdAt;
	}

	public String getText() {
		return text;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
