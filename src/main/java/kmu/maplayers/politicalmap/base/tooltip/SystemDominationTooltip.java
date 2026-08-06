package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;

import kmu.maplayers.base.tooltip.MapHoverTooltip;

import java.util.Optional;

/**
 * The domination breakdown a political-map layer shows for the hovered star system: the groups holding
 * markets there strongest first, each a crest, a name, and a score matching the weights the map paints
 * its fills by. The one {@link MapHoverTooltip} the faction and alliance views both inject - it adapts
 * flat vs nested off the active view's grouping, so those two layers share one tooltip that varies its
 * content rather than each carrying its own.
 *
 * <p>The two-tier shape is settled where the group's kind is known ({@link StandingRowResolver}) rather
 * than here: a lone-faction group (the faction view) resolves to an entry made up of nothing and reads
 * as one flat line, while an alliance bloc resolves to one carrying its member factions and reads as a
 * line over them, however few it holds.
 *
 * <p>So this box adds nothing to the shape every standings box shares
 * ({@link SystemStandingsTooltip}), and that is what it is: a group listed as the line naming it and
 * nothing beneath, which is the shared default rather than an answer of its own. What it decides is
 * how much of the contest one hover states - who holds the system, on the score alone - and where the
 * rest of that answer is to be found: the account behind those scores is the counterpart box's
 * ({@link ExpandedSystemDominationTooltip}), which the framework draws in place of this one while the
 * player has asked for it, so the ordinary hover stays a glance and the detail is there for the asking
 * rather than always on screen.
 *
 * <p>Stateless past the reader it is built around - the view, the live economy, and the settings are
 * read afresh each paint - so one shared instance serves both views.
 */
public final class SystemDominationTooltip extends SystemStandingsTooltip {

    /** The one shared instance; stateless, so both views inject it. */
    public static final SystemDominationTooltip INSTANCE =
        new SystemDominationTooltip(VANILLA_CLAIM_BREAKDOWN_READER);

    // The counterpart drawn in this box's place while the player has asked for detail. Built here on
    // this box's own claim read rather than reached for as a shared instance, so the pair can never
    // answer a decree from two different readers - which is the whole point of the layer binding one.
    private final ExpandedSystemDominationTooltip expandedVariant;

    SystemDominationTooltip(ClaimBreakdownReader claimBreakdownReader) {
        super(claimBreakdownReader);
        this.expandedVariant = new ExpandedSystemDominationTooltip(claimBreakdownReader);
    }

    @Override
    public Optional<MapHoverTooltip> resolveExpandedVariant() {
        return Optional.of(expandedVariant);
    }
}
