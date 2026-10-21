package kmu.maplayers.base.render.clusters;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.render.clusters.FillSplit.FillState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@link FillState} each system of a map draws in inside its cluster's one border.
 *
 * <p>Holds only the systems that draw as something other than solid, and answers solid for every
 * other system - an owned one the layer said nothing special about, an unowned one, and a cell with
 * no star of its own alike. Listing the solid ones would add entries carrying nothing the default
 * does not, while still leaving the unowned and starless cells to the default.
 *
 * <p>One state per system rather than one set per state, so a system cannot be named hatched and
 * unfilled at once. Which systems take which state is the layer's call; this only carries it from
 * whatever decides it to the fill split that reads it.
 *
 * <p>Immutable: the map is copied on the way in.
 */
public final class SystemFillStates {

    /** A map whose every system fills solid. */
    public static final SystemFillStates ALL_SOLID = new SystemFillStates(Map.of());

    private final Map<SystemKey, FillState> nonSolidFillStateBySystemKey;

    private SystemFillStates(Map<SystemKey, FillState> nonSolidFillStateBySystemKey) {
        this.nonSolidFillStateBySystemKey = nonSolidFillStateBySystemKey;
    }

    /**
     * The fill states a layer stated, copied so a later write to its map moves nothing here.
     *
     * <p>A solid entry is dropped rather than kept, solid being what every unnamed system answers
     * already.
     *
     * @param fillStateBySystemKey the state of each system the layer names, in the order named
     * @return the fill states, answering solid for any system not named
     */
    public static SystemFillStates createOf(Map<SystemKey, FillState> fillStateBySystemKey) {

        var nonSolidFillStateBySystemKey = new LinkedHashMap<SystemKey, FillState>();

        for (var entry : fillStateBySystemKey.entrySet()) {

            if (entry.getValue() != FillState.SOLID) {
                nonSolidFillStateBySystemKey.put(entry.getKey(), entry.getValue());
            }
        }

        return new SystemFillStates(Collections.unmodifiableMap(nonSolidFillStateBySystemKey));
    }

    /**
     * How many systems draw in one state other than solid, for a log line stating what a
     * resolution decided. Solid is never counted: it is every system the map was not told about.
     *
     * @param fillState the state to count, other than solid
     * @return how many systems were named in it
     */
    public int countSystemsIn(FillState fillState) {

        var count = 0;

        for (var nonSolidFillState : nonSolidFillStateBySystemKey.values()) {

            if (nonSolidFillState == fillState) {
                count++;
            }
        }

        return count;
    }

    // Equal when they name the same systems in the same states, so a resolution holding these
    // compares by what it says, as the records around it do.
    @Override
    public boolean equals(Object other) {

        return other instanceof SystemFillStates otherFillStates
            && nonSolidFillStateBySystemKey.equals(otherFillStates.nonSolidFillStateBySystemKey);
    }

    /**
     * @return the state of each system that draws as something other than solid, read-only and in
     *         the order the layer named them; what a layer extending another's answer copies
     */
    public Map<SystemKey, FillState> getNonSolidFillStateBySystemKey() {
        return nonSolidFillStateBySystemKey;
    }

    @Override
    public int hashCode() {
        return nonSolidFillStateBySystemKey.hashCode();
    }

    /**
     * The state one system draws in.
     *
     * <p>A cell with no star of its own has no per-system fill state, so it fills solid with the rest
     * of its cluster rather than probing the map with a null key.
     *
     * @param systemKey the system a member cell draws as, or null for a cell with no star of its own
     * @return the state it draws in; solid for any system not named
     */
    public FillState resolveFillStateOf(SystemKey systemKey) {

        if (systemKey == null) {
            return FillState.SOLID;
        }
        return nonSolidFillStateBySystemKey.getOrDefault(systemKey, FillState.SOLID);
    }
}
