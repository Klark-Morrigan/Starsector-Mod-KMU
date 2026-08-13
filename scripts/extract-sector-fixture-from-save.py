"""Regenerates the political map's sector test fixture from a Starsector save.

    python scripts/extract-sector-fixture-from-save.py <path-to-campaign.xml> [out.csv]

Default output is a new fixture beside the ones the tests already read, under
src/utils/resources/kmu/maplayers/base/geometry/, named for the sector it describes
(sector-<n>systems-<n>owned-<n>blocs-<m>of<d>factions.csv). The tests run over EVERY
fixture in that folder, so extracting another save adds coverage rather than replacing
it: a second
sector with a different density, a different empty-space ratio, or a different bloc count
exercises shapes the first one never produces. The name carries the metrics because that
is what distinguishes one sector's geometry from another's - a save name would not.

WHY THIS IS A SCRIPT AND NOT PART OF THE BUILD
It reads the save's obfuscated XStream serialisation, which is not a contract: class
aliases (Sstm, cL), the id/ref scheme, and where a given object first appears all shift
with the game version and the mod set. Wiring that into the suite would let an unrelated
game update turn every geometry test red. So this runs by hand, its output is committed,
and no test ever opens a save.

THREE THINGS THE FORMAT DOES THAT ARE EASY TO GET WRONG
  1. XStream writes each object once, with z="<id>", and refers back with ref="<id>". It
     writes it at the object's FIRST appearance in document order, which for a star system
     can be inside something unrelated - a planet's containing location, or a Nexerelin
     intel entry. Systems must therefore be resolved through the id map, never by assuming
     where they are nested.
  2. An object is named after the FIELD it was first written into, not after its class. A
     system's hyperspace anchor appears under several element names, so tokens are matched
     structurally (a loc, a containing location, and an orbit) rather than by tag.
  3. A market carries <location>, holding its containing system's hyperspace point. That is
     the join key between a market and its system, and it avoids walking the entity graph
     from planet to system entirely.

SCORE APPROXIMATION
The fixture's score is the winning faction's summed market size. The real weight
(DominanceRules) is that size times a colony multiplier, plus station and patrol size
points, minus a low-stability cut, all driven by live settings. The approximation is here
so the geometry runs over a realistic SPREAD of bloc strengths; it is not a reference for
what dominance resolves to, and nothing should assert against it as though it were.
"""
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

DEFAULT_OUT_DIR = Path("src/utils/resources/kmu/maplayers/base/geometry")
# A market owned by one of these holds no political ground - a derelict or an abandoned
# station - so it must not make a system read as owned.
UNOWNED_FACTIONS = {None, "", "neutral"}
SEPARATOR = ","
# A couple of systems are legitimately unpairable - their anchor is not a hyperspace token
# at all - so the pairing check is a floor rather than equality. It exists because this
# walk's failure mode is silence: it once paired 178 of 200 on one file and 200 of 200 on
# that save's own .bak, and the only symptom was a fixture 22 systems short that still
# looked entirely well-formed.
MINIMUM_PAIRED_FRACTION = 0.97


def resolve(elem, by_id):
    """Follows an XStream ref="N" to the element that actually holds the data."""
    if elem is None:
        return None
    ref = elem.get("ref")
    return by_id.get(ref) if ref is not None else elem


def has_class(elem, alias):
    """Whether an element is an instance of an obfuscated class, written either way.

    XStream names an object after the FIELD it was written into and puts the class in cl=
    (<s cl="Sstm" z="5">), EXCEPT where the field's declared type already implies it, in
    which case the element is named after the class and carries no cl= at all
    (<Sstm z="5">). Testing cl= alone therefore silently drops every object of the second
    kind, and which kind a given system takes depends on where it happens to be written -
    so the same sector reports a different size from one save to the next.
    """
    return elem is not None and (elem.get("cl") == alias or elem.tag == alias)


def count_defined_factions(root):
    """How many factions the save defines at all - the sector's modding weight.

    This counts faction objects, not faction ids seen in passing: an id appears in fleets,
    memory keys and mod bookkeeping for factions that hold nothing, so counting ids
    overstates. Uses has_class because a faction is written both ways, the same trap that
    once cost 45% of the star systems.
    """
    return sum(1 for e in root.iter() if has_class(e, "Faction") and e.get("z") is not None)


def collect_market_owning_factions(root):
    """The factions holding at least one market anywhere - including deep-space stations.

    Deliberately not the same as the factions the political map colours. A faction can hold
    markets only in systems another faction dominates, and it then owns ground while
    colouring none of it. The gap between the two is worth seeing in a fixture's name.
    """
    owners = set()
    for elem in root.iter():
        if elem.tag not in ("Market", "market") or elem.get("z") is None:
            continue
        if elem.tag == "market" and elem.get("cl") not in ("Market", "PCMarket"):
            continue
        faction = elem.find("factionId")
        if faction is not None and faction.text and faction.text.strip() not in UNOWNED_FACTIONS:
            owners.add(faction.text.strip())
    return owners


def count_star_systems(root):
    """How many star systems the save holds, counted independently of the pairing walk.

    The walk can only report systems whose hyperspace anchor it managed to pair. If it
    pairs fewer than exist, it degrades silently - a smaller sector that still looks
    perfectly well-formed - so the two counts are compared before anything is written.
    """
    return sum(1 for e in root.iter() if has_class(e, "Sstm") and e.get("z") is not None)


def collect_sites(root, by_id):
    """Every star system's hyperspace point, keyed by its 'x|y' string.

    Tokens are found structurally rather than by tag: see note 2 in the module docstring.

    The two class tests here look inconsistent and are not. `where` is read WITHOUT
    resolving, because XStream repeats cl= on the reference itself
    (<where cl="Hyperspace" ref="2"/>) while the target it points at is named after its own
    field and carries no class at all - so resolving first would throw the answer away. The
    orbit's system must be resolved, because the class is only knowable from the
    definition, and there it may be written either way, hence has_class.
    """
    sites = {}
    for token in root.iter():
        loc = token.find("loc")
        where = token.find("where")
        orbit = token.find("orbit")
        if loc is None or where is None or orbit is None or not loc.text:
            continue
        if where.get("cl") != "Hyperspace":
            continue
        system = resolve(orbit.find("s"), by_id)
        if not has_class(system, "Sstm"):
            continue
        sites[loc.text.strip()] = system.get("bN") or system.get("dN") or loc.text.strip()
    return sites


def collect_weights(root):
    """Each system's per-faction market weight, keyed by the system's 'x|y' string.

    Markets appear under several element names (<Market>, and <market> holding a Market or
    a PCMarket), so they are gathered by class. A ref= occurrence repeats an object already
    counted and carries no fields, so only definitions are read.
    """
    weights = defaultdict(lambda: defaultdict(int))
    counts = defaultdict(int)
    for elem in root.iter():
        if elem.tag not in ("Market", "market") or elem.get("z") is None:
            continue
        if elem.tag == "market" and elem.get("cl") not in ("Market", "PCMarket"):
            continue
        size = elem.find("size")
        faction = elem.find("factionId")
        location = elem.find("location")
        if size is None or faction is None or location is None or not location.text:
            continue
        if faction.text in UNOWNED_FACTIONS:
            continue
        key = location.text.strip()
        weights[key][faction.text] += int(size.text)
        counts[key] += 1
    return weights, counts


def disambiguate_ids(systems):
    """Suffixes repeated names so each row has a unique id.

    Star system NAMES are not unique - a sector carries several "Deep Space" and several
    "Unknown Location" - while the ids the real map keys cells by are. Names are kept
    because a failing test naming "Askonia" beats one naming an opaque handle, so a repeat
    takes a #N suffix in a stable order rather than silently collapsing into one entry.
    """
    seen = defaultdict(int)
    for system in systems:
        seen[system["id"]] += 1
        if seen[system["id"]] > 1:
            system["id"] = f"{system['id']}#{seen[system['id']]}"
    ids = [s["id"] for s in systems]
    if len(set(ids)) != len(ids):
        raise SystemExit("ids still collide after disambiguation")


def build_rows(sites, weights, counts):
    systems = []
    for key, name in sites.items():
        x, y = (float(v) for v in key.split("|"))
        by_faction = weights.get(key)
        if by_faction:
            owner, score = max(by_faction.items(), key=lambda kv: kv[1])
        else:
            # Uninhabited or decivilised-only. A score of 0 is the frontier case the
            # political map's whole dead-star treatment is built around.
            owner, score = "", 0
        systems.append({"id": name, "x": x, "y": y, "owner": owner, "score": score,
                        "markets": counts.get(key, 0)})
    systems.sort(key=lambda s: (s["id"], s["x"], s["y"]))
    disambiguate_ids(systems)
    return systems


def build_fixture_name(systems, market_factions, defined_factions):
    """Names a fixture for the sector it describes, so two are told apart by what differs.

    The first three numbers are the geometry's own character - how many cells, how much of
    the map is owned, how many blocs meet along its borders. Naming by save or by date would
    file two identical sectors under different names and two different sectors under similar
    ones.

    The trailing "<m>of<d>factions" is provenance rather than content: how many of the
    sector's defined factions hold a market. It says how heavily modded the save this came
    from was, and how much of that content reached the economy - which is what decides
    whether a fixture is a stress case or a quiet one. The file itself holds only the
    dominant owner per system, so these two are describing the source, not the rows.
    """
    owned = sum(1 for s in systems if s["score"] > 0)
    blocs = len({s["owner"] for s in systems if s["owner"]})
    return (f"sector-{len(systems)}systems-{owned}owned-{blocs}blocs"
            f"-{len(market_factions)}of{defined_factions}factions.csv")


def write_fixture(target, systems, market_factions, defined_factions):
    owned = sum(1 for s in systems if s["score"] > 0)
    blocs = len({s["owner"] for s in systems if s["owner"]})
    header = [
        f"# Source sector: {defined_factions} factions defined, {len(market_factions)} hold",
        f"# at least one market, {blocs} dominate at least one system and so colour the map.",
        "# The first two say how heavily modded the save was and how much of that content",
        "# reached the economy; only the third is in the rows below. They are in the file",
        "# name too, so one fixture is told from another without opening it.",
        "#",
        "# Real sector geometry: every star system's hyperspace site, the bloc holding it,",
        "# and that bloc's dominance weight in it. Regenerate with",
        "# scripts/extract-sector-fixture-from-save.py; the save parser is deliberately not part of",
        "# the build, so this file is the fixture's only source and no test opens a save.",
        "#",
        "# The score is the winning faction's summed market size - the dominant term of the",
        "# real DominanceRules weight, not the whole of it. It is here so a test has a",
        "# realistic SPREAD of blocs and strengths to run over, not so a test can assert",
        "# what dominance resolves to; assert that against SystemDominance, never here.",
        "#",
        f"# An empty groupKey is an unowned system - uninhabited or decivilised-only - which",
        f"# is the frontier case, and {len(systems) - owned} of these {len(systems)} are one.",
        "#",
        "# The id is the system's name, which the sector does NOT keep unique - a repeat",
        "# carries a #N suffix. The real map keys cells by a unique system id; names are",
        "# used here only so a failure reads as \"Askonia\" rather than as an opaque handle.",
        "#",
        "# id,x,y,groupKey,score",
    ]
    rows = [f"{s['id']},{s['x']:.1f},{s['y']:.1f},{s['owner']},{s['score']}" for s in systems]
    for system in systems:
        if SEPARATOR in system["id"] or SEPARATOR in system["owner"]:
            raise SystemExit(f"id/owner contains the separator: {system['id']}")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text("\n".join(header + rows) + "\n", encoding="ascii", newline="\n")
    print(f"wrote {target}: {len(systems)} systems, {owned} owned, {len(systems) - owned} empty")


def check_pairing(sites, declared):
    """Refuses to write a fixture that quietly lost systems.

    Two ways this goes wrong, and neither announces itself. The walk can fail to pair a
    system's anchor after a game or mod update moves where objects are written - the output
    is then just a smaller sector, still well-formed. And a save being written while the
    game holds it open parses cleanly but is missing whole swathes of the sector, so a
    fixture taken from one is quietly not the sector it claims to be. A handful of systems
    are legitimately unpairable (their anchor is written outside hyperspace), so the check
    is a floor rather than equality.
    """
    if declared and len(sites) < declared * MINIMUM_PAIRED_FRACTION:
        raise SystemExit(
            f"paired only {len(sites)} of {declared} star systems. Either the save format "
            f"moved, or the save was mid-write - close Starsector before extracting, then "
            f"retry. Refusing to write a fixture from a sector this incomplete.")


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    save = Path(sys.argv[1])
    print(f"parsing {save} ...", flush=True)
    root = ET.parse(save).getroot()
    by_id = {e.get("z"): e for e in root.iter() if e.get("z") is not None}
    print(f"indexed {len(by_id)} objects", flush=True)
    sites = collect_sites(root, by_id)
    declared = count_star_systems(root)
    print(f"paired {len(sites)} of {declared} star systems", flush=True)
    check_pairing(sites, declared)
    weights, counts = collect_weights(root)
    unmatched = sum(1 for key in weights if key not in sites)
    if unmatched:
        # Markets whose point is no star system: deep-space and hyperspace stations, which
        # seed no cell on the political map, so dropping them is correct rather than a loss.
        print(f"note: {unmatched} market sites match no star system (deep-space stations)")
    systems = build_rows(sites, weights, counts)
    market_factions = collect_market_owning_factions(root)
    defined_factions = count_defined_factions(root)
    blocs = len({s["owner"] for s in systems if s["owner"]})
    print(f"factions: {defined_factions} defined, {len(market_factions)} hold a market, "
          f"{blocs} dominate a system")
    target = (Path(sys.argv[2]) if len(sys.argv) > 2
              else DEFAULT_OUT_DIR / build_fixture_name(
                  systems, market_factions, defined_factions))
    write_fixture(target, systems, market_factions, defined_factions)


main()
