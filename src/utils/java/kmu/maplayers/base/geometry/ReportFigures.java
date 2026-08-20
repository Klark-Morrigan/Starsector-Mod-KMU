package kmu.maplayers.base.geometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How a population of numbers is read out, for every report that reads one out.
 *
 * <p>Nothing here is about the map. These are the two things every measure ends in - a value
 * out of a sorted population, and a row of numbers to print - and each report that wrote its
 * own came to word an empty population differently from the one beside it.
 */
final class ReportFigures {

    private ReportFigures() {
    }

    /**
     * One value out of a sorted population, at a fraction of the way along it.
     *
     * <p>Nothing where the population is empty. That is not defensive padding: a sector whose
     * cells are all lone islands traces no coast at all, so the frontages it offers are none -
     * and a report is meant to say so rather than to throw on the way past.
     *
     * @param sorted   the population, already in order
     * @param fraction how far along it to read, from 0 to 1
     * @return the value there, or zero where there is no population
     */
    static double findPercentile(List<Double> sorted, double fraction) {

        if (sorted.isEmpty()) {
            return 0;
        }

        return sorted.get((int) Math.min(
            sorted.size() - 1.0,
            Math.floor(fraction * (sorted.size() - 1))));
    }

    /**
     * The middle of an unsorted population.
     *
     * @param values the population, in any order
     * @return its median, or zero where there is no population
     */
    static double findMedian(List<Double> values) {

        var sorted = new ArrayList<>(values);
        sorted.sort(Double::compare);

        return findPercentile(sorted, 0.5);
    }

    /**
     * A row of whole numbers, space separated.
     *
     * @param values    the numbers, in the order they should read
     * @param whenEmpty what to say when there are none, which is what varies between callers
     * @return the row
     */
    static String joinRounded(List<Double> values, String whenEmpty) {

        var listed = new ArrayList<String>(values.size());

        for (var value : values) {
            listed.add(String.format(Locale.ROOT, "%.0f", value));
        }
        return joinSpaced(listed, whenEmpty);
    }

    /**
     * A row of anything already worded, space separated.
     *
     * @param values    the entries, in the order they should read
     * @param whenEmpty what to say when there are none
     * @return the row
     */
    static String joinSpaced(List<String> values, String whenEmpty) {

        return values.isEmpty() ? whenEmpty : String.join(" ", values);
    }
}
