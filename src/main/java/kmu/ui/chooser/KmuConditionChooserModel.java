package kmu.ui.chooser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class KmuConditionChooserModel {
    private final List<KmuConditionChooserEntry> entries;

    public KmuConditionChooserModel(List<KmuConditionChooserEntry> entries) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(entries, "entries")));
    }

    public List<KmuConditionChooserEntry> getEntries() {
        return entries;
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
}
