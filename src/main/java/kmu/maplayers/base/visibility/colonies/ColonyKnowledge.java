package kmu.maplayers.base.visibility.colonies;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.colonies.Colonies;
import kmlib.starsector.colonies.Colony;
import kmlib.starsector.colonies.KnownColonyReader;
import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.markets.MarketVisibility;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What the player may be told about a colony set: the rule in force, what has been observed of the
 * sector, and the projections both are spent on.
 *
 * <p>The framing the map puts over the library's colony set. The set says what is present; this
 * says what may be drawn from it - which colonies may be named in a box, and which amount to people
 * living somewhere. Named once here so "what counts on the map" cannot drift between the cell that
 * paints a system, the ribbon that counts in it and the box that names its factions.
 *
 * <p>Knowledge is the composition of two facts, not a choice between them: the colony must have
 * been found, and the kinds a bare fog would leak must additionally have been observed. So a
 * widened gate can never show what has not been found.
 *
 * <p>Being found is itself two routes. The fog is the ordinary one; the other is somebody living
 * in the same place having reported the colony standing there, which reaches only the kinds that
 * say so and only while the player has asked for no more than a sighting is worth. A report widens
 * where a gate narrows, which is why it sits inside the first fact rather than beside the second.
 *
 * <p>The rule and the register travel together because every projection spends both, and one
 * without the other answers nothing: a gate with no observations behind it holds back everything it
 * covers for good, and observations with no rule to read them decide nothing.
 *
 * <p>The alliances beside them are the second sector-scoped fact of the same shape, folded once
 * where the register is opened and spent on the same question: who standing in a place would speak
 * about whatever else is there. Held here rather than on the rule, since who stands with whom is
 * what the sector is doing rather than what the player has asked to be shown.
 *
 * <p>A class rather than a record because it remembers as well as carries. Each colony's kind is
 * resolved on the first ask and kept for the rest of the pass, which is what makes the map's own
 * classification affordable over a set that no longer stores it - and what keeps two readers of one
 * colony from arriving at two answers. Built for one pass and discarded with it, since the kinds
 * are a snapshot of the sector that pass read. Not safe for concurrent use, a pass being one
 * thread's work.
 */
public final class ColonyKnowledge implements KnownColonyReader {

    // The kind test of a projection that turns nothing away for what it is - the known reading,
    // where the visibility rule is the whole of the filter.
    private static final Predicate<ColonyKind> EVERY_KIND = kind -> true;

    // Each colony's kind, resolved on first ask and remembered for the rest of the pass.
    //
    // Keyed by identity rather than by market id, because the colonies of one pass are the very
    // objects the colony index memoised: two indistinguishable twin colonies are distinct entries
    // the set kept apart, and an id-keyed memo would let one answer for the other. A colony that
    // outlives the pass is nobody's concern - the pass is discarded with it.
    private final Map<Colony, ColonyKind> kindByColony = new IdentityHashMap<>();

    private final ColonyVisibility rule;
    private final ColonySightings sightings;
    private final FactionAlliances alliances;

    /**
     * Pairs a rule with the register it is read against, among factions standing alone.
     *
     * @param rule      what the player may be shown; null reads as {@link ColonyVisibility#BASE_FOG}
     * @param sightings what has been observed and where; null reads as
     *                  {@link ColonySightings#NONE}, which withholds a gated colony rather than
     *                  leaking one
     */
    public ColonyKnowledge(ColonyVisibility rule, ColonySightings sightings) {
        this(rule, sightings, FactionAlliances.NONE);
    }

    /**
     * Pairs a rule with the register it is read against and the alliances standing while it is
     * read.
     *
     * @param rule      what the player may be shown; null reads as {@link ColonyVisibility#BASE_FOG}
     * @param sightings what has been observed and where; null reads as
     *                  {@link ColonySightings#NONE}, which withholds a gated colony rather than
     *                  leaking one
     * @param alliances who stands with whom; null reads as {@link FactionAlliances#NONE}, which
     *                  credits every other faction present with speaking
     */
    public ColonyKnowledge(
            ColonyVisibility rule,
            ColonySightings sightings,
            FactionAlliances alliances) {

        this.rule = rule == null ? ColonyVisibility.BASE_FOG : rule;
        this.sightings = sightings == null ? ColonySightings.NONE : sightings;
        this.alliances = alliances == null ? FactionAlliances.NONE : alliances;
    }

    /**
     * Opens the knowledge one pass reads under: its rule, against the sector's own register, among
     * the alliances standing at that moment.
     *
     * <p>The one place the register is opened for a pass, because the sector is in hand here and
     * nowhere below - a colony carries no route back to it, so a projection asked later would have
     * nowhere to read what has been observed from. The alliances are folded here for the same
     * reason and with the same effect: every projection of one pass answers under one set of them.
     *
     * @param sector the sector whose register is read; null reads as nothing observed
     * @param rule   what the player may be shown of a colony
     * @return the knowledge that pass answers under
     */
    public static ColonyKnowledge over(SectorAPI sector, ColonyVisibility rule) {

        return new ColonyKnowledge(
            rule,
            SectorColonySightings.readSightings(sector),
            FactionAllianceRegistry.readAlliances());
    }

    /**
     * The knowledge an observer standing in a place reads under: the fog alone, no register, and
     * the alliances standing at this moment.
     *
     * <p>What the sighting register itself writes through. An observation is made by being
     * somewhere, so no reveal and no gate has anything to say about it - and letting a rule reach
     * that write would put observations nobody made permanently into a save, where turning the
     * reveal off again could not take them out.
     *
     * <p>The alliances do reach it, because they say who made the observation rather than what may
     * be done with one. An ally that would not speak has not observed anything on the player's
     * behalf, so recording one here would write down the very announcement the rule declines to
     * credit.
     *
     * @return knowledge classifying colonies and answering the gates, under the fog and nothing
     *         else
     */
    public static ColonyKnowledge observingUnderTheFog() {

        return new ColonyKnowledge(
            ColonyVisibility.BASE_FOG,
            ColonySightings.NONE,
            FactionAllianceRegistry.readAlliances());
    }

    /**
     * The rule this knowledge reads under.
     *
     * @return what the player may be shown of a colony - the reveal, the survey level and the gates
     */
    public ColonyVisibility rule() {
        return rule;
    }

    /**
     * What has been observed of the sector's colonies and where.
     *
     * <p>Published for a reader that states how old the news of a colony is, which no visibility
     * rule ever asks: the register is read here once per pass, so a reader taking it from the
     * knowledge is reading the very observations the projection beside it was resolved against
     * rather than opening the sector's memory a second time.
     *
     * @return the register this knowledge reads against; never null
     */
    public ColonySightings sightings() {
        return sightings;
    }

    /**
     * What kind of place a colony is, resolved once per pass.
     *
     * @param colony the colony to classify; null reads as {@link ColonyKind#COLONY}, the safe
     *               direction
     * @return the kind the map tells this colony apart by
     */
    public ColonyKind readKindOf(Colony colony) {

        if (colony == null) {
            return ColonyKind.COLONY;
        }
        return kindByColony.computeIfAbsent(
            colony,
            found -> ColonyKind.resolveKind(found.market(), found.isListedByEconomy()));
    }

    /**
     * The colonies the player may be shown - the known projection of the set, and the one every
     * display reader is meant to take rather than filtering the whole set itself.
     *
     * <p>Resolved in two passes, because revelation has a route that runs through the place itself.
     * The first settles what the ungated colonies show; the second judges the gated ones against
     * it. That is an ordering rather than a cycle - an ordinary open colony is never gated, so what
     * settles a place is settled without consulting anything a gate decided.
     *
     * @param colonies the place's colonies, as one walk of it reported; null holds nothing
     * @return the colonies the rule admits, in the set's own order
     */
    @Override
    public List<Colony> readKnownColonies(Colonies colonies) {
        return readColoniesPassing(colonies, EVERY_KIND);
    }

    /**
     * Whether anyone the player knows of is here - the emptiness of {@link #readKnownColonies}
     * asked without materialising it.
     *
     * <p>Offered because "does anyone live in this system" is asked of every system in the sector
     * on a scan, and per cell while the map is drawn, where the projection's contents are never
     * wanted - only whether it has any.
     *
     * <p>Stops at the first colony that passes, the place's settling owners having been folded for
     * the whole set beforehand - so the answer cannot differ from the listing's own emptiness,
     * whichever way a gate falls.
     *
     * @param colonies the place's colonies; null holds nothing
     * @return true when at least one colony passes the projection
     */
    public boolean hasKnownColony(Colonies colonies) {
        return hasColonyPassing(colonies, EVERY_KIND);
    }

    /**
     * The colonies that amount to people living here - the known projection minus the derelicts
     * nobody was ever aboard, and the one every reader answering a question about habitation is
     * meant to take.
     *
     * <p>A second named projection rather than a flag on the first, because what may be
     * <em>said</em> about a place and what constitutes <em>habitation</em> of it are different
     * questions with different answers. A derelict is named in a listing once somebody has seen
     * it and settles nothing whatever, so a reader handed the wrong one of these makes a claim
     * about the sector rather than a formatting mistake.
     *
     * <p>Resolved by the same walk under the same rule, differing from the known listing in its
     * kind test alone, so the two cannot disagree about what has been found or revealed. A
     * derelict a settled place reveals is therefore admitted to the listing only, and goes on
     * settling nothing - which is what keeps one derelict from vouching for another.
     *
     * @param colonies the place's colonies; null holds nothing
     * @return the known colonies somebody lives on, in the set's own order
     */
    public List<Colony> readInhabitingColonies(Colonies colonies) {
        return readColoniesPassing(colonies, ColonyKnowledge::isInhabitingKind);
    }

    /**
     * Whether anybody the player knows of lives here - the emptiness of
     * {@link #readInhabitingColonies} asked without materialising it.
     *
     * <p>Offered for the same reason its known counterpart is: the cell that paints a place and
     * the scan that decides whether to draw it at all ask only whether the projection has any,
     * per system and per frame.
     *
     * @param colonies the place's colonies; null holds nothing
     * @return true when at least one colony passes the habitation projection
     */
    public boolean hasInhabitingColony(Colonies colonies) {
        return hasColonyPassing(colonies, ColonyKnowledge::isInhabitingKind);
    }

    /**
     * The colonies some gate holds back until they have been observed - and so, for anybody
     * standing in the place this set was read out of, what they have just observed that is worth
     * writing down.
     *
     * <p>Being somewhere is seeing what is in it, which is why this reads no fog and no rule. A
     * concealed base the observer cannot pick out is recorded all the same: an observation is not
     * by itself permission to show anything, the gate being a further condition on top of the fog
     * rather than an alternative to it.
     *
     * <p>The gated shapes alone, which is one shape fewer than the inhabitants' read records. A
     * collapsed world is found on a report as well, but the player standing here is the very act
     * vanilla already stamps a permanent survey level for - so an entry would restate what the fog
     * answers anyway, at the cost of one per collapsed world in every system ever entered.
     *
     * @param colonies the place's colonies; null holds nothing
     * @return the colonies a gate covers, in the set's own order
     */
    public List<Colony> readGatedColonies(Colonies colonies) {
        return collectColonies(colonies, this::isGatedColony);
    }

    /**
     * The colonies worth recording that the place's own inhabitants can see standing here - what
     * somebody living beside a derelict, a concealed base or a collapsed world has observed, whether
     * or not the player ever comes.
     *
     * <p>Owner-aware through the same test the rule uses: the people keeping a secret are exactly
     * the ones a faction-blind reading would credit with telling it. So a faction's open colony
     * observes a rival's concealed base beside it, and never its own or an ally's.
     *
     * <p>Read under the fog alone, whatever rule this knowledge carries. A reveal changes what may
     * be shown and never what was seen, so letting one reach here would write down observations
     * nobody made - permanently, into a save, where turning the reveal off again could not take
     * them out.
     *
     * @param colonies the place's colonies; null holds nothing
     * @return the colonies worth recording that somebody living here has seen, in the set's own
     *         order
     */
    public List<Colony> readColoniesObservedByInhabitants(Colonies colonies) {

        var settlingOwnerIds = readSettlingOwnerIds(colonies, ColonyVisibility.BASE_FOG);

        return collectColonies(
            colonies,
            colony -> isWorthRecording(colony)
                && isObservedByInhabitants(colony, settlingOwnerIds));
    }

    // What one projection admits, as a single test over a colony: the rule resolved, the place's
    // settling owners folded once for the whole set, and the kind this projection wants.
    //
    // Built once and handed to both walks below rather than restated in each, because the listing
    // and the emptiness question about it agreeing is the whole reason the projection is named
    // here - and a second statement of the filter is precisely how that agreement would be lost.
    // The two projections then differ in their kind test alone.
    private Predicate<Colony> buildProjectionFilter(
            Colonies colonies,
            Predicate<ColonyKind> isWantedKind) {

        var settlingOwnerIds = readSettlingOwnerIds(colonies, rule);

        return colony -> isWantedKind.test(readKindOf(colony))
            && isKnownColony(colony, settlingOwnerIds);
    }

    // Materialises one projection.
    private List<Colony> readColoniesPassing(
            Colonies colonies,
            Predicate<ColonyKind> isWantedKind) {

        return collectColonies(colonies, buildProjectionFilter(colonies, isWantedKind));
    }

    // Materialises whatever a test admits, the test having been assembled by the caller. Named
    // apart from the projection read above rather than overloading it, the two differing in what
    // their argument means - a kind to want, against the whole of the filter.
    //
    // Walked in the set's own order rather than gated colonies after ungated ones, since a
    // caller mirroring vanilla's tie rules reads that order and would resolve differently.
    private static List<Colony> collectColonies(
            Colonies colonies,
            Predicate<Colony> isPassingColony) {

        if (colonies == null) {
            return List.of();
        }
        var passingColonies = new ArrayList<Colony>();

        for (var colony : colonies.colonies()) {

            if (isPassingColony.test(colony)) {
                passingColonies.add(colony);
            }
        }
        return List.copyOf(passingColonies);
    }

    // The emptiness of one projection, stopped at the first colony that passes.
    //
    // Its own walk rather than the listing's isEmpty, because the cell that paints a place and
    // the scan that decides whether to draw it at all ask this per system and per frame, and
    // never want the contents.
    private boolean hasColonyPassing(Colonies colonies, Predicate<ColonyKind> isWantedKind) {

        if (colonies == null) {
            return false;
        }
        var isPassingColony = buildProjectionFilter(colonies, isWantedKind);

        for (var colony : colonies.colonies()) {

            if (isPassingColony.test(colony)) {
                return true;
            }
        }
        return false;
    }

    // The first pass: who is here whose word about whatever else stands in this place would
    // reach the player - the owners of the ungated colonies the fog admits.
    //
    // Read off what the projection shows rather than off what is present, because word reaches
    // the player through colonies the player knows are inhabited. A place whose only ordinary
    // colony is itself undiscovered has no grapevine the player is party to, and letting it
    // reveal anything would have the map act on a fact the player has no means of holding.
    //
    // Their owners rather than a bare "somebody is here", because whose colony it is decides
    // whom it can speak for. Folded once for the whole set: a place holds a handful of owners at
    // most, and every gated colony asks the same fold.
    private Set<String> readSettlingOwnerIds(Colonies colonies, ColonyVisibility fogRule) {

        var settlingOwnerIds = new HashSet<String>();

        if (colonies == null) {
            return settlingOwnerIds;
        }
        for (var colony : colonies.colonies()) {

            if (isSettlingColony(colony, fogRule)) {
                settlingOwnerIds.add(colony.readOwnerId());
            }
        }
        return settlingOwnerIds;
    }

    // A colony that makes its place settled: somewhere people are, held in the open, and shown
    // by the fog. Its kind and its openness are exactly what keeps it out of every gate, which
    // is why the pass over these can be read before any gate is decided.
    //
    // A collapsed colony would not qualify - its people are still there and have nothing left to
    // carry word through; a derelict never had anybody; and a concealed colony is not the sector's
    // town crier. Which kinds have somebody to talk is the kind's own answer rather than a
    // comparison written here, so a kind added later is not left out of it by omission.
    private boolean isSettlingColony(Colony colony, ColonyVisibility fogRule) {

        return readKindOf(colony).isSettlingLocation()
            && !colony.isHidden()
            && isAdmittedByFog(colony, fogRule);
    }

    // The projection's rule for one colony, stated once so the read that materialises the
    // projection and the emptiness question about it cannot answer under different filters -
    // which is the very drift naming the projection here exists to prevent.
    //
    // Written over the kind, the two gates and the place's settling owners rather than as a
    // branch per surface, so a fourth kind or a third gate has one place to be added.
    private boolean isKnownColony(Colony colony, Set<String> settlingOwnerIds) {

        if (!isFoundColony(colony, settlingOwnerIds)) {
            return false;
        }
        // A reveal reaches the fog and stops there. It says a colony may be shown though nobody
        // has found it, which is no answer at all to whether anybody has seen it standing here -
        // and a reveal that quietly cleared a gate beside it would show a player a second thing
        // they never asked for, with nothing on screen to say why it appeared.
        return !isGatedOnRevelation(colony)
            || isObserved(colony, settlingOwnerIds);
    }

    // Whether the colony has been found at all: the fog, or a report from the place's own
    // inhabitants standing in for it.
    //
    // A collapsed colony is admitted by vanilla's survey level alone, and vanilla writes that
    // level for player acts only - so the world stays off the map while the faction's colony
    // orbiting beside it is drawn, though anybody living there can plainly see the ruin.
    //
    // Somebody's word is worth a sighting and no survey, which leaves one bar this really decides.
    // At the lowest the fog asks for no survey at all and admits the world outright, so this arm
    // is never reached; at the two above it the player has asked for readings nobody's presence
    // produces, and it is refused. The route earns its keep at the bar in between, which is the
    // one the map ships on.
    //
    // Which kinds a report can reach is the kind's own answer, and no kind that says yes settles
    // its place - so nothing found this way ever joins the owners folded for the first pass, and
    // can vouch neither for itself nor for anything standing beside it. The two passes stay an
    // ordering rather than becoming a cycle.
    //
    // A colony found this way that also conceals itself meets the same observation twice, once
    // here and once at the gate below. Deliberate: both ask literally whether somebody saw it
    // standing there, and a second class of observation invented to keep them apart would be two
    // names for one fact.
    private boolean isFoundColony(Colony colony, Set<String> settlingOwnerIds) {

        return isAdmittedByFog(colony, rule)
            || (readKindOf(colony).isFoundByReport()
                && DecivilisedMarkets.isMetBySighting(rule.ungovernedColonySurveyLevel())
                && isObserved(colony, settlingOwnerIds));
    }

    // Whether a colony amounts to people living where it stands - the one thing that separates
    // the habitation projection from the known one.
    //
    // Stated as the exclusion of the kind nobody was ever aboard rather than as an admission of
    // the ordinary one, so a kind added later inhabits its place unless it says otherwise.
    // Overstating a place by one hulk is the cheaper mistake; erasing a settlement that is really
    // there takes its people with it.
    private static boolean isInhabitingKind(ColonyKind kind) {
        return kind != ColonyKind.SPACE_DERELICT;
    }

    // The base fog, plus the admission arm the composed filter carries with it.
    //
    // Routed on the kind because one kind is found by a different act. Every other colony is found
    // by discovering its entity; a collapsed colony is found by surveying its world closely enough
    // to see what became of it, and its market is owned by nobody and condition-only, which the
    // ordinary composition refuses outright. Two named compositions rather than a branch spelled
    // out here, so what "found" means for each kind is stated where the market's own facts are.
    //
    // The admission arm is re-asked although the set is already selected on it: the composition is
    // what the rule is, and unpicking it here to save the second read would leave a narrower
    // statement of "counts as a known colony" living in this class.
    private boolean isAdmittedByFog(Colony colony, ColonyVisibility fogRule) {

        if (readKindOf(colony) == ColonyKind.UNGOVERNED_COLONY) {
            return MarketVisibility.isCountedAsUngovernedColony(
                colony.market(),
                fogRule.shouldIncludeUndiscoveredMarkets(),
                fogRule.ungovernedColonySurveyLevel());
        }
        return MarketVisibility.isCountedAsColony(
            colony.market(),
            fogRule.shouldIncludeUndiscoveredMarkets());
    }

    // Whether an observation of this colony decides anything - the one question the sighting
    // register exists to answer, and so the one thing worth an entry.
    //
    // Two shapes qualify, for opposite reasons: a gated colony is withheld until somebody has seen
    // it, and a colony a report can find is admitted because somebody has. Stated once, since a
    // register holding one of the two under a rule spending both would leave the missing half
    // answering only while a witness was still alive. An open colony the economy lists is
    // permanently in the sector's own sight, so an entry for one would answer nothing while costing
    // an entry per colony in the sector.
    private boolean isWorthRecording(Colony colony) {
        return isGatedColony(colony) || readKindOf(colony).isFoundByReport();
    }

    // Whether any gate at all covers this colony, whatever the rule happens to be holding back.
    // Asked of the gates themselves rather than branched on here, so what a gate covers is stated
    // where the gate is named and a third one needs no edit in this class.
    private boolean isGatedColony(Colony colony) {
        return RevelationGate.isGatedColony(colony, readKindOf(colony));
    }

    // Whether any gate the rule carries is about this colony - the narrower question, since a gate
    // the player has switched off holds nothing back.
    private boolean isGatedOnRevelation(Colony colony) {

        var kind = readKindOf(colony);

        for (var gate : rule.revelationGates()) {

            if (gate.coversColony(colony, kind)) {
                return true;
            }
        }
        return false;
    }

    // Somebody has seen this colony where it now stands.
    //
    // One idea reached two ways rather than two unlike terms: the place is being looked at right
    // now, or an observation of it standing here was written down at some point. Both are
    // observations, and both routes that make one - the player's presence and the place's own
    // inhabitants - write into the same register, so what is known does not evaporate when the
    // informant dies.
    //
    // The live term is kept beside the recorded one so a colony arriving among witnesses is shown
    // at once rather than at whatever cadence the recorder happens to run on, and so the rule goes
    // on answering in a sector where nothing has recorded anything at all.
    private boolean isObserved(Colony colony, Set<String> settlingOwnerIds) {
        return isObservedByInhabitants(colony, settlingOwnerIds)
            || isSighted(colony);
    }

    // Whether somebody living in this place would speak about this colony: a shown, open colony
    // stands here whose owner is on the other side from its own.
    //
    // Owner-aware rather than a bare "anybody lives here", because the people keeping a secret
    // are exactly the ones a faction-blind test credits with telling it. A pirate base in a
    // system the pirates openly hold would otherwise be announced to the player by the pirates,
    // which is the one case the route was never arguing for - a rival's colony in the same place
    // does talk, and that is what the route is for.
    //
    // The exception is read at the size the world keeps it, an ally being no likelier to hand
    // over a partner's concealed base than the partner is: an alliance is a standing arrangement
    // to act as one, and selling out a partner is the thing it forbids. So a member of the same
    // alliance is passed over exactly as the owner itself is, and the base falls back to the only
    // witness the route should ever have credited it to - the player's own sighting.
    //
    // A derelict is held by nobody in particular, and one other shape falls to that same nobody:
    // a station no faction holds that the economy lists anyway, which is read as kept and so
    // settles its place. That pair is the only arrangement in which this comparison reaches a
    // derelict, and it withholds - a hulk on the books is no witness to the hulk beside it.
    // Nobody joins no alliance, so the widened test leaves a derelict answering exactly as the
    // bare owner comparison did.
    private boolean isObservedByInhabitants(Colony colony, Set<String> settlingOwnerIds) {

        var ownerId = colony.readOwnerId();

        for (var settlingOwnerId : settlingOwnerIds) {

            if (!Objects.equals(settlingOwnerId, ownerId)
                    && !alliances.areFactionsAllied(settlingOwnerId, ownerId)) {

                return true;
            }
        }
        return false;
    }

    // Whether this colony has been observed where it now stands - the recorded half of what makes
    // a gated colony known.
    //
    // The observation has to name the place the colony is in today, not merely some place it was
    // once seen in. A colony that has moved since is unseen again until somebody meets it where it
    // has gone, and one founded after the last observation was never seen at all - both of which a
    // bare "has this system been entered" would answer wrongly, and in opposite directions.
    //
    // A colony in no star system - hyperspace, where mods put a few - reads sighted. There is no
    // system to have been in and none to be settled, so a gate answering otherwise would hold such
    // a colony back for good rather than until somebody saw it.
    private boolean isSighted(Colony colony) {

        if (!(colony.market().getContainingLocation() instanceof StarSystemAPI system)) {
            return true;
        }
        var observation = sightings.readObservation(colony.market().getId());

        return observation != null && observation.locationId().equals(system.getId());
    }
}
