package io.github.jespersm.twca.unselectinator.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable snapshot of all tracked select and load activity for one observation.
 */
public class LazySelectReport {
    private final int totalSelectCount;
    private final List<TrackedLoadEvent> loadEvents;
    private final List<LazySelectEvent> lazySelectEvents;
    private final List<TrackedRelationEdge> relationEdges;

    public LazySelectReport(int totalSelectCount,
                            List<TrackedLoadEvent> loadEvents,
                            List<LazySelectEvent> lazySelectEvents,
                            List<TrackedRelationEdge> relationEdges) {
        this.totalSelectCount = totalSelectCount;
        this.loadEvents = List.copyOf(loadEvents);
        this.lazySelectEvents = List.copyOf(lazySelectEvents);
        this.relationEdges = List.copyOf(relationEdges);
    }

    public int getTotalSelectCount() {
        return totalSelectCount;
    }

    public List<TrackedLoadEvent> getLoadEvents() {
        return new ArrayList<>(loadEvents);
    }

    public List<LazySelectEvent> getLazySelectEvents() {
        return new ArrayList<>(lazySelectEvents);
    }

    public List<TrackedRelationEdge> getRelationEdges() {
        return new ArrayList<>(relationEdges);
    }

    public int getLazySelectCount() {
        return lazySelectEvents.size();
    }

    public long countLazySelectsByRelation(String relationName) {
        return lazySelectEvents.stream()
                .filter(event -> Objects.equals(event.relationName(), relationName))
                .count();
    }

    public long countLazySelectsInitiatedBy(String endpointSignature) {
        return lazySelectEvents.stream()
                .filter(event -> event.initiatingEndpoint() != null)
                .filter(event -> endpointSignature.equals(event.initiatingEndpoint().signature()))
                .count();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("LazySelectReport{totalSelectCount=")
                .append(totalSelectCount)
                .append(", lazySelectCount=")
                .append(lazySelectEvents.size())
                .append('}');

        if (!lazySelectEvents.isEmpty()) {
            builder.append("\n")
                    .append(lazySelectEvents.stream()
                            .map(event -> " - " + event.kind() + " "
                                    + (event.owner() != null ? event.owner().displayName() : "<unknown-owner>")
                                    + "." + event.relationName()
                                    + " via " + event.initiatingEndpoint().displayName())
                            .collect(Collectors.joining("\n")));
        }

        return builder.toString();
    }
}

