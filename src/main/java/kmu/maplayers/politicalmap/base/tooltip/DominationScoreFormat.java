package kmu.maplayers.politicalmap.base.tooltip;

import java.util.Locale;

/**
 * Formats a domination score for display in the hover tooltip.
 *
 * <p>The single presentation decision for a score, kept out of the aggregation that produces it: a
 * score is a whole weight, shown grouped by thousands so a large tally reads at a glance. Fixing the
 * grouping on {@link Locale#ROOT} keeps the separator a comma whatever the JVM's default locale,
 * matching the numeric style the rest of the map's text is written in.
 */
public final class DominationScoreFormat {

    // "%,d" inserts the locale's grouping separator every three digits. The separator only appears
    // once a score reaches four figures, so a smaller score renders bare and grouping shows only
    // when it is warranted.
    private static final String GROUPED_INTEGER = "%,d";

    private DominationScoreFormat() {
        // utility class, no instances.
    }

    /**
     * @param score the domination weight to render
     * @return the score as a whole number grouped by thousands, e.g. {@code 1,234}
     */
    public static String formatScore(int score) {
        return String.format(Locale.ROOT, GROUPED_INTEGER, score);
    }
}
