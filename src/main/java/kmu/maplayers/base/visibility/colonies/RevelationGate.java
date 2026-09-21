package kmu.maplayers.base.visibility.colonies;

import kmlib.starsector.markets.colonies.Colony;

/**
 * A shape of colony a bare fog would leak, and the gate that holds it back until somebody has
 * seen it where it stands.
 *
 * <p>Named rather than carried as a boolean apiece because two adjacent booleans are
 * transposable without failing: a rule built with the derelict gate and the concealment gate the
 * wrong way round means something quite different, and compiles, passes and ships. A set of
 * these cannot be got wrong that way, and a third gate becomes one constant here rather than a
 * fourth flag every construction of a rule has to be corrected for.
 *
 * <p>Each gate states what it covers, so the rule applying them asks the gate rather than
 * branching per kind. The two answers differ in nature - one reads what kind of place the colony
 * is, the other reads whether its market conceals itself - which is why neither is derivable
 * from the other, and why a colony can fall under both at once.
 *
 * <p>A gate narrows and never widens. Carrying one holds its shape back until it is revealed;
 * leaving it out drops that shape to the fog alone rather than admitting anything the fog
 * refuses.
 */
public enum RevelationGate {

    /**
     * A derelict nobody ever lived on. The fog admits one the moment its entity is found, and a
     * great many modded stations are never discoverable at all - so ungated, every derelict in the
     * sector is on the map from the first frame of a campaign.
     *
     * <p>A station a faction keeps is not one of these and is not held back here. It wears the same
     * condition and is somebody's, which is what the kind read parts on, so it passes this gate on
     * the strength of its owner and answers to the concealment gate alone.
     */
    SPACE_DERELICTS {

        @Override
        boolean coversColony(Colony colony, ColonyKind kind) {
            return kind == ColonyKind.SPACE_DERELICT;
        }
    },

    /**
     * A colony that hides itself. Concealment and discovery disagree on these: a colony hidden
     * on an entity that was never discoverable is admitted by the fog on that technicality, so
     * ungated, a secret or wandering colony is shown wherever it currently stands.
     */
    HIDDEN_COLONIES {

        @Override
        boolean coversColony(Colony colony, ColonyKind kind) {
            return colony.isHidden();
        }
    };

    /**
     * Whether any gate at all is about this colony - the shape whose observations are worth
     * writing down, as against the shape a rule happens to be holding back at the moment.
     *
     * <p>Asked of every gate rather than of one rule's set, because a gate turned off is a choice
     * about what to show and never about what was seen. A register written under the live setting
     * would have nothing recorded for the years a gate spent off, and would then hold back a
     * colony the player had been looking at the whole time.
     *
     * <p>An ungated colony is pointedly not one of these. A colony held in the open and listed by
     * the economy is permanently in the sector's own sight, so an observation of one answers
     * nothing any rule asks while costing an entry per colony in the sector.
     *
     * @param colony the colony to test; a null one is covered by nothing
     * @param kind   what kind of place that colony is, as the pass classified it
     * @return true when some gate would hold this colony back until it had been observed
     */
    public static boolean isGatedColony(Colony colony, ColonyKind kind) {

        if (colony == null) {
            return false;
        }
        for (var gate : values()) {

            if (gate.coversColony(colony, kind)) {
                return true;
            }
        }
        return false;
    }

    // Whether this gate is about the given colony at all. Package-private because which colonies
    // a gate covers is the visibility rule's own business, and the rule lives beside it - a
    // caller outside has the gate to name, not a colony to test against it.
    abstract boolean coversColony(Colony colony, ColonyKind kind);
}
