package kmu.conditions;

import java.util.Objects;

public final class KmuConditionSpec {
    private final String id;
    private final String name;
    private final String icon;
    private final String description;
    private final String sourceModName;
    private final boolean planetary;

    public KmuConditionSpec(String id, String name, String icon, boolean planetary) {
        this(id, name, icon, null, null, planetary);
    }

    public KmuConditionSpec(
            String id,
            String name,
            String icon,
            String description,
            String sourceModName,
            boolean planetary) {
        this.id = requireNonBlank(id, "id");
        this.name = name == null || name.trim().isEmpty() ? this.id : name;
        this.icon = icon;
        this.description = normalizeOptional(description);
        this.sourceModName = normalizeOptional(sourceModName);
        this.planetary = planetary;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }

    public String getSourceModName() {
        return sourceModName;
    }

    public boolean isPlanetary() {
        return planetary;
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof KmuConditionSpec)) {
            return false;
        }
        KmuConditionSpec that = (KmuConditionSpec) other;
        return planetary == that.planetary
                && id.equals(that.id)
                && name.equals(that.name)
                && Objects.equals(icon, that.icon)
                && Objects.equals(description, that.description)
                && Objects.equals(sourceModName, that.sourceModName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, icon, description, sourceModName, planetary);
    }

    @Override
    public String toString() {
        return "KmuConditionSpec{"
                + "id='" + id + '\''
                + ", name='" + name + '\''
                + ", icon='" + icon + '\''
                + ", description='" + description + '\''
                + ", sourceModName='" + sourceModName + '\''
                + ", planetary=" + planetary
                + '}';
    }
}
