package kmu.starsector;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.characters.RelationshipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.Locale;

import static kmu.KmuValues.readTextOrNull;
import static kmu.KmuValues.readValueOrNull;

public final class StarsectorPlayerRelationshipFormatter {
    private static final int MAX_RELATIONSHIP_REPUTATION = 100;
    private static final String PLAYER_RELATIONSHIP_DESCRIPTION_FORMAT = "%s (%d / %d)";

    public RelationshipSummary formatPlayerRelationship(FactionAPI faction) {
        if (faction == null) {
            return RelationshipSummary.createEmptySummary();
        }

        RelationshipAPI relationship = readValueOrNull(faction::getRelToPlayer);
        if (relationship != null) {
            RepLevel level = readValueOrNull(relationship::getLevel);
            Integer repInt = readValueOrNull(relationship::getRepInt);
            Color color = readValueOrNull(relationship::getRelColor);
            if (level != null && repInt != null) {
                return new RelationshipSummary(formatRelationshipDescription(level, repInt), color);
            }
        }

        Float rel = readValueOrNull(() -> faction.getRelationship(Factions.PLAYER));
        if (rel == null) {
            return RelationshipSummary.createEmptySummary();
        }

        RepLevel level = RepLevel.getLevelFor(rel);
        if (level == null) {
            return RelationshipSummary.createEmptySummary();
        }
        int repInt = RepLevel.getRepInt(rel);
        Color color = readValueOrNull(() -> faction.getRelColor(Factions.PLAYER));
        if (color == null) {
            color = readValueOrNull(() -> Misc.getRelColor(rel));
        }
        return new RelationshipSummary(formatRelationshipDescription(level, repInt), color);
    }

    private String formatRelationshipDescription(RepLevel level, int repInt) {
        String levelName = readTextOrNull(level::getDisplayName);
        if (levelName == null) {
            levelName = level.name();
        }
        return String.format(
                Locale.ROOT,
                PLAYER_RELATIONSHIP_DESCRIPTION_FORMAT,
                levelName,
                repInt,
                MAX_RELATIONSHIP_REPUTATION);
    }

    public static final class RelationshipSummary {
        private final String description;
        private final Color color;

        private RelationshipSummary(String description, Color color) {
            this.description = description;
            this.color = color;
        }

        private static RelationshipSummary createEmptySummary() {
            return new RelationshipSummary(null, null);
        }

        public String getDescription() {
            return description;
        }

        public Color getColor() {
            return color;
        }
    }
}
