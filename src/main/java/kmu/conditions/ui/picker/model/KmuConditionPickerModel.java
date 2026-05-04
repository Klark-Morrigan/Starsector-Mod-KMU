package kmu.conditions.ui.picker.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static kmu.KmuValues.normalizeText;

public final class KmuConditionPickerModel {
    private final List<KmuConditionPickerEntry> entries;
    private final KmuConditionPickerLocation location;

    public KmuConditionPickerModel(
            List<KmuConditionPickerEntry> entries,
            KmuConditionPickerLocation location) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(entries, "entries")));
        this.location = Objects.requireNonNull(location, "location");
    }

    public List<KmuConditionPickerEntry> getEntries() {
        return entries;
    }

    public KmuConditionPickerLocation getLocation() {
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
                .filter(KmuConditionPickerEntry::isPresent)
                .count();
    }

    public int getAbsentCount() {
        return getEntryCount() - getPresentCount();
    }

    public int getHiddenCount() {
        return (int) entries.stream()
                .filter(KmuConditionPickerEntry::isHidden)
                .count();
    }

    public int getSuppressedCount() {
        return (int) entries.stream()
                .filter(KmuConditionPickerEntry::isSuppressed)
                .count();
    }

    public int getVisibleCount() {
        return (int) entries.stream()
                .filter(e -> e.isPresent() && !e.isHidden())
                .count();
    }

    public int getAvailableCount() {
        return (int) entries.stream()
                .filter(e -> !e.isPresent())
                .count();
    }

    public Optional<KmuConditionPickerEntry> findEntry(String conditionId) {
        String normalizedId = normalizeText(conditionId);
        if (normalizedId == null) {
            return Optional.empty();
        }
        return entries.stream()
                .filter(entry -> entry.getConditionId().equals(normalizedId))
                .findFirst();
    }
}
