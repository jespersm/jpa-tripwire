package io.github.jespersm.jpa.tripwire.unselectinator.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable snapshot of all tracked select and load activity for one observation.
 *
 * @param totalSelectCount total number of tracked SELECT statements
 * @param loadEvents tracked entity/collection load events
 * @param lazySelectEvents lazy select events detected outside explicit fetch scopes
 * @param relationEdges observed owner->relation->target edges
 */
public record LazySelectReport(int totalSelectCount, List<TrackedLoadEvent> loadEvents,
                               List<LazySelectEvent> lazySelectEvents, List<TrackedRelationEdge> relationEdges) {
    /**
     * Canonical constructor with defensive copying.
     *
     * @param totalSelectCount total number of tracked SELECT statements
     * @param loadEvents tracked entity/collection load events
     * @param lazySelectEvents lazy select events detected outside explicit fetch scopes
     * @param relationEdges observed owner->relation->target edges
     */
    public LazySelectReport(int totalSelectCount,
                            List<TrackedLoadEvent> loadEvents,
                            List<LazySelectEvent> lazySelectEvents,
                            List<TrackedRelationEdge> relationEdges) {
        this.totalSelectCount = totalSelectCount;
        this.loadEvents = List.copyOf(loadEvents);
        this.lazySelectEvents = List.copyOf(lazySelectEvents);
        this.relationEdges = List.copyOf(relationEdges);
    }

    @Override
    public List<TrackedLoadEvent> loadEvents() {
        return new ArrayList<>(loadEvents);
    }

    @Override
    public List<LazySelectEvent> lazySelectEvents() {
        return new ArrayList<>(lazySelectEvents);
    }

    @Override
    public List<TrackedRelationEdge> relationEdges() {
        return new ArrayList<>(relationEdges);
    }

    /** @return number of lazy-select events in this report */
    public int getLazySelectCount() {
        return lazySelectEvents.size();
    }

    /**
     * Count lazy select events for a specific relation name.
     *
     * @param relationName relation field/role name
     * @return matching lazy-select count
     */
    public long countLazySelectsByRelation(String relationName) {
        return lazySelectEvents.stream()
                .filter(event -> Objects.equals(event.relationName(), relationName))
                .count();
    }

    /**
     * Count lazy select events associated with a specific endpoint signature.
     *
     * @param endpointSignature endpoint signature in the form {@code ownerType#methodName}
     * @return matching lazy-select count
     */
    public long countLazySelectsInitiatedBy(String endpointSignature) {
        return lazySelectEvents.stream()
                .filter(event -> event.attributions().isEmpty()
                        ? event.initiatingEndpoint() != null && endpointSignature.equals(event.initiatingEndpoint().signature())
                        : event.attributions().stream().map(a -> a.origin().initiatingEndpoint())
                        .anyMatch(endpoint -> endpoint != null && endpointSignature.equals(endpoint.signature())))
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
                                    + " triggered at " + (event.triggerLocation() != null
                                            ? event.triggerLocation().toString() : "<unknown>")
                                    + " via " + (event.initiatingEndpoint() != null
                                            ? event.initiatingEndpoint().toString() : "<unknown or ambiguous origin>")
                                    + (event.attributions().isEmpty() ? "" : " origins=" + event.attributions()))
                            .collect(Collectors.joining("\n")));
        }

        return builder.toString();
    }
}

