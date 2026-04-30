package kmu.ui.chooser.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class KmuConditionChooserModel {
    private final List<KmuConditionChooserEntry> entries;
    private final KmuConditionChooserLocation location;

    public KmuConditionChooserModel(List<KmuConditionChooserEntry> entries) {
        this(entries, KmuConditionChooserLocation.unknown());
    }

    public KmuConditionChooserModel(
            List<KmuConditionChooserEntry> entries,
            KmuConditionChooserLocation location) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(entries, "entries")));
        this.location = Objects.requireNonNull(location, "location");
    }

    public List<KmuConditionChooserEntry> getEntries() {
        return entries;
    }

    public KmuConditionChooserLocation getLocation() {
        return location;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int getEntryCount() {
        return entries.size();
    }

    public int getPresentCount() {
        return (int) entries.stream()
                .filter(KmuConditionChooserEntry::isPresent)
                .count();
    }

    public int getAbsentCount() {
        return getEntryCount() - getPresentCount();
    }

    public int getHiddenCount() {
        return (int) entries.stream()
                .filter(KmuConditionChooserEntry::isHidden)
                .count();
    }

    public int getSuppressedCount() {
        return (int) entries.stream()
                .filter(KmuConditionChooserEntry::isSuppressed)
                .count();
    }

    public Optional<KmuConditionChooserEntry> findEntry(String conditionId) {
        if (conditionId == null || conditionId.trim().isEmpty()) {
            return Optional.empty();
        }
        String normalizedId = conditionId.trim();
        return entries.stream()
                .filter(entry -> entry.getConditionId().equals(normalizedId))
                .findFirst();
    }
}
