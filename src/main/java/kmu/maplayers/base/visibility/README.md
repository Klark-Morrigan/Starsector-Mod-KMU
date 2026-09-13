# What the map may say (`maplayers/base/visibility`)

What a layer is allowed to state about a place,
as against what the sector holds.
The engine answers who owns a colony and what is standing in a system
whether or not the player has ever been there,
so a layer drawing straight off it paints knowledge nobody in the game has.
Everything here stands between the two:
the sector's own facts go in,
and what a surface may draw comes out.

Part of [the map layers](../../README.md).

## Index

- [The families are disjoint at admission](#the-families-are-disjoint-at-admission)
- [What is drawn at all (`systems`)](#what-is-drawn-at-all-systems)
- [Colonies (`colonies`)](#colonies-colonies)
- [Structures (`structures`)](#structures-structures)
- [Installations (`installations`)](#installations-installations)
- [The shared register (`observations`)](#the-shared-register-observations)
- [Which way a default errs](#which-way-a-default-errs)
- [The edges](#the-edges)

## The families are disjoint at admission

Three kinds of place are listed,
and which one a thing is decided by admission rather than by convention:

| Family | Admitted by | Owner |
| --- | --- | --- |
| Colony | carrying a `MarketAPI` | the market's faction |
| Structure | carrying `Tags.OBJECTIVE` | whoever last held it, which churns off-screen |
| Installation | market-less, not `objective`, and station-shaped | its own faction, or whatever is posted at it |

The overlap is real,
which is why admission is stated rather than assumed.
A vanilla `comm_relay` is a market-less custom entity,
so a walk selecting on "no market" alone swallows the whole structure set;
`IndEvo_Watchtower` is `objective`-tagged and is a structure in every sense,
while `IndEvo_ArtilleryStation` carries `station` and no `objective` and is an installation.
A place admitted to two families would be stated twice on one row,
in two voices.

Each family gets its own knowledge and its own rule rather than a union type over places.
The subjects have nothing in common but the register beneath them,
and a union would buy one signature at the price of a null arm in every reader.

## What is drawn at all (`systems`)

Which star systems a layer draws.
`MapVisibility` admits a system on any of four paths -
reached by an ordinary jump point and drawn by the vanilla map,
lit by an active gate,
reached by an installed mod's own route,
or inhabited.
The first three are KMLib's reachability arms taken singly rather than through the fold that sums them,
since only one of the three has to compose with the draw check.
`MapVisibilityFingerprint` hashes the admitted set into the scalar that says it moved,
`MapVisibilityPass` is one reading of the sector answering that rule,
and `DrawnSystemPositions` reads each drawn system's live hyperspace position off it.

The rule takes the answers rather than the sector to read them from -
a pass holds the colony index and hyperspace scan it composes them off -
so a caller running several walks in one tick selects each system
once between them and asks one thing which systems are drawn.
The positions come off the index's own traversal rather than one opened here,
which is what keeps a pass to the single traversal a frame allows it however many of its readers want the sector's systems.

They come off it keyed by `SystemKey`,
because a star system id is not unique:
a live modded sector lists several systems under one -
vanilla's own unnamed deep space and abyssal systems among them -
and a point cloud gathered under ids is short a site for each,
which draws as a system with no cell on a map that cells every neighbour it has.
The partition is keyed the same way,
so the positions reach it under the address it holds its cells by.

The fingerprint contributes under that same key for the same reason,
and its version of the loss is quieter:
a colliding pair keyed by id contributes one value twice,
so one of them entering the drawn set as the other left would move nothing at all,
and the map would go on drawing a system that is no longer there with no rebuild ever asked for.

Two arrival paths do not also have to be drawn,
and for one reason:
each is itself something the player is shown,
which the vanilla draw check -
asking only whether a star or a cloud is painted at that point -
cannot know about.

A lit gate joins the network every other gate lists,
so a player standing at any gate is shown this one
and can conclude the system is there whatever the map paints where it sits.
Composed with the draw check instead,
a gate the player has just lit would lead somewhere the map draws as nothing.
KMLib publishes it as `StarSystems.hasActiveGate` beside the reachability read that also consults it.

A mod-made destination is reached by an entity of the mod's own rather than a jump point,
and marked by an icon of the mod's own rather than a star anchor,
so both vanilla reads answer no about a place plainly on the map;
KMLib's `SystemAccessRoutes` answers for both at once,
and an install running no such mod consults nothing.

Inhabited means somebody lives there -
the colony set's habitation projection -
so a system whose only market is an abandoned station is admitted by access alone,
and a star-hidden one holding a derelict is not drawn at all.
`MapVisibilityRules` pairs the colony rule that judges that with the force override a caller may admit a system outright by,
so a layer can widen what is drawn without the rule knowing why it wanted to.

## Colonies (`colonies`)

What may be shown of a colony,
which is the map's own framing and not the sector's.
KMLib states which colonies a place holds;
`ColonyKind` says what kind of place each one stands for,
`ColonyVisibility` and `RevelationGate` say what may be shown of it,
and `ColonyKnowledge` pairs that rule with the sector's record of what has been observed
and publishes the two projections every surface reads -
the known listing a box may name,
and the habitation reading a cell is settled by.
Each pass opens one and classifies each colony once through it.

Being found and being revealed stay separate questions there.
The fog answers the first for nearly everything;
a collapsed colony,
which vanilla admits on a survey level it writes for player acts only,
may instead be found on the word of whoever else lives in the same system,
so the ruin in orbit is drawn beside the colony that can see it.
`ColonyKind` says which kinds that reaches,
and only while the survey asked for is no more than a sighting is worth.

`ColonyKindLookup` folds those kinds by colony id for a reader that meets a colony as a row rather than as a colony,
and `ColonyDiscoveryLookup` folds the entity's own found-or-not flag the same way for the same reader.
`OpenlyKnownColonyLookup` folds a third such answer -
whether a concealed colony is one the sector openly points at,
off the entity ids and tag `OpenlyKnownColonyRegistry` is seeded with at start-up.
That one excuses a word a hover box would otherwise say and reaches no gate:
a landmark is concealed to every rule here,
exactly as the base beside it is.

Who would speak about what stands beside them is owner-aware and then some:
`FactionAlliances` says which factions stand together,
read through the `FactionAllianceSource` port a composition root registers with `FactionAllianceRegistry`,
so a partner keeps a concealed base quiet exactly as its own faction does.
It is a world fact rather than a rule,
so it is folded per pass beside the observations rather than carried on `ColonyVisibility` -
and read through a port so the rule never names the mod that maintains an alliance,
nor changes with which map layer the player is looking at.

The register behind the observations is `ColonySightings` over `SectorColonySightings`,
written by `ColonySightingRecorder` as the player travels
and by the substrate's own poll (`base/refresh`) for what a place's own inhabitants can see;
`ColonySightingInstaller` stands the travelling half up on load.
The player's arrival writes the gated shapes alone;
the inhabitants' sweep also writes the collapsed worlds their word is the only thing showing,
so one outlives the last neighbour that could report it.
Its entries sit in the shared `ObservationStore` under a key of its own,
each spelt by `ColonyObservationCodec` -
the moment,
then the place -
and `PresentColonies` is what a load asks which colonies the sector still holds,
read off the raw market listings
so a superseded market that may yet win its place is not shed as gone.

## Structures (`structures`)

What was last observed of a built structure:
a comm relay,
a nav buoy,
a sensor array.
KMLib states which structures a place holds and what each one plainly is;
the register here says what one looked like when somebody last established it,
which is the only way its holder can be stated without either naming a faction in a system nobody has approached or reporting a handover that happened
while the player was a sector away.

A `StructureObservation` carries the holder,
whatever `StructureFault` the structure was found in,
and a moment per axis:
who holds it is established by standing there,
whether it works by anyone living in the same system,
and the two go stale independently -
a relay visited once and watched since has a current state beside a four-cycle-old holder.
A running hack is deliberately not among its fields,
for the reason stated there.
`StructureObservations` is the port a rule reads the register through,
over `SectorStructureObservations`,
whose entries sit in the shared `ObservationStore` under a key of its own and are spelt by `StructureObservationCodec` -
the two moments,
the state letters,
then the holder id to the end of the entry.

## Installations (`installations`)

Market-less station entities:
the hulks,
habitats and defensive platforms that carry no market and so reach none of the colony machinery.
`InstallationKind` is what one counts as -
a `DERELICT` that counts for nobody,
a `HELD` one a faction owns outright,
a `GARRISONED` one held for whoever posted what is standing there.

No single tag selects the family,
so admission has a hand-set half.
`InstallationOverrideTable` is what the shipped `data/config/kmu/installations.csv` states type by type -
whether the map lists it,
and what it counts as -
`InstallationOverride` one row of it,
and `InstallationOverrideTableReader` the read.
It is a *merged* spreadsheet:
any mod shipping the same path adds its own rows,
so a mod whose station carries no tag costs one row that its own author can write.
A row may state one column and leave the other,
research into what a modded entity really is arriving one question at a time.

## The shared register (`observations`)

How old the news about one concealed fact is,
stated once for every family that conceals one.
`ObservationRecency` is the triad it can be in -
something is revealing it now,
the record recalls it from a moment,
or nothing ever established it -
sealed so a fourth state cannot be added without every reader being asked about it,
and folded rather than switched over since the mod targets Java
17. `ObservationRecency.resolveRecency` is the one place a live reading is ranked above a record and a record above nothing.
    `RevealedFact` pairs that state with the value an axis conceals,
    where it conceals one;
    a fact nobody ever established cannot be built holding a value,
    so the words for an unknown are the reader's to supply.

`ObservationNoteFormatter` puts an age into words -
how long ago,
and on what date -
in the three span words every axis shares,
taking the lead-in that introduces them as a key from its caller.
Which words introduce a date belong to the axis;
how long a day is does not.
`ObservationNotes` settles which of a row's several axes dates it,
each arriving as an `ObservationAxis` pairing a recency with its own lead-in:
every recalled axis carrying a moment contributes it,
the most recent wins and is stated in that axis's words,
and a row nothing contributes to carries no date.
A current axis and an unknown one both contribute nothing,
so a row is dated by what it recalls and by nothing else.

What an axis recalls is kept by `ObservationStore`,
the register every family writes its observations into:
sector memory under a key of its own,
held as text so no class name of ours is baked into a save,
and never opened at all where there was nothing to record.
`RecordedObservations` is what a pass reads it back through.
What one entry means stays with the family,
as an `ObservationCodec` the store is handed -
which is also where the fixed-fields-first convention lives,
the free-form field running to the end of an entry
so an id spelt with the separator reads back whole,
and an entry that does not part reading as the weaker true thing rather than as corrupt.
The lifecycle is the store's because every family wants the same one:
a load sheds what the register no longer describes
and then records what the player was left standing among,
in that order,
asking the family which of its subjects still exist and what is being observed now.

## Which way a default errs

Every fallback here points the same way:
understate what is known,
never invent it.
An unclassified colony reads as an ordinary colony,
because misfiling a derelict overstates a place by one hulk
while misfiling a colony erases the people on it.
An installation nothing classifies is `DERELICT` at nought weight,
because inventing a holder for an unresearched entity type hands somebody a system inside a number the player cannot check.
A register that will not read answers "never observed"
rather than failing the pass that consulted it.

## The edges

`colonies` may not import `systems`,
and `observations` may not import `colonies`,
`structures` or `systems` -
the register is the shared half
and would stop being shareable the moment it named one of its families.
Declared in [`gradle/package-layering.gradle`](../../../../../../../gradle/package-layering.gradle)
and enforced by the build.
