# Refresh (`maplayers/base/refresh`)

What says a cached overlay has gone stale,
and the throttled polls that find the changes the engine announces to nobody.
Which signals exist,
who declares which,
and the four rebuild paths they drive are [the caching notes](../../../../../../../docs/dev/caching.md)';
this register owns the poll shape and the one poll that is nobody's layer.

Part of [the map layers framework](../../README.md).

## Index

- [The signals and the board](#the-signals-and-the-board)
- [The poll](#the-poll)
- [The cadence is the player's](#the-cadence-is-the-players)
- [Two scripts, two classes](#two-scripts-two-classes)
- [The substrate's poll](#the-substrates-poll)

## The signals and the board

A signal is a `MapLayerRefreshSignal`,
declared by the framework (`MapLayerCommonRefreshSignal`) or by the layer that alone means anything by it.
`MapLayerRefreshBoard` is what a signal is raised on,
one per sector held by [that sector's machinery](../machinery/README.md),
since the stale set names systems by `SystemKey`,
whose engine-minted arms each sector mints without regard to another's.
Every producer marking a system stale holds the system when it does so -
the market listeners have it from the event,
the staleness poll walks the sector -
so each states the key rather than the vanilla ID,
and nothing downstream has to widen an ID back into the systems sharing it.
Every producer is handed the board it means,
and this package is gated from importing the machinery one so none can resolve a board of its own.
`RefreshSignalRevisions` is a reading of where the traced signals stood,
for a cache to name what moved since it last rebuilt.

## The poll

Some changes come with no engine event at all -
a gate activating,
a system cut off,
a colony founded in a system already drawn -
so the only way to find them is to re-read the sector on a cadence
and diff it against the last read.
`StalenessPollLoop` is that cadence:
a throttle of a few campaign seconds -
[how few being the player's](#the-cadence-is-the-players) -
a fault guard that says once per session which source faulted and keeps polling,
and the call into a `MapLayerStalenessSource`.
What to re-read,
what counts as a change and which signal each change earns are the source's answers,
so the loop names no layer and no signal.

`MovingSystems` rides the political map's poll as a passenger:
it stages which drawn systems rewrite their own hyperspace position,
so the geometry leaves a mover out of the partition rather than chase it.
A mover is named by `SystemKey`,
since a system ID is not unique and two systems sharing one would otherwise be one observation -
a move by either reading as a move by whichever the sector lists last.

## The cadence is the player's

`KmuMapRefreshSettings` holds it,
one `Int` row on `Map - Dev` in seconds,
and every poll reads it through `StalenessPollLoop`.
What the throttle trades is a reading of the whole sector against how long a change takes to show,
and how that trade falls depends on how big the sector is and what else is installed -
which is a measurement rather than a taste,
so it is a knob rather than a constant.

**One knob rather than the window.**
The spread between the two ends is jitter,
so several polls advanced by the same frame do not all elapse on one,
and the far end is derived from the floor at a fixed ratio.
A second row for it would only ever be set a little past the first,
and the shipped 4-5 second window is exactly that ratio over the shipped floor,
so an untouched setting polls as it always has.

**The floor is above zero.**
Zero polls every frame on the campaign thread,
which would make a diagnostics row a frame-cost hazard -
the one value a cadence must not offer.
The ceiling is low for a reason of its own:
the polls are chained,
the substrate writing what a system's inhabitants saw and a layer's own pass reading the gate that reads it,
so a fact reaches the map after as much as two periods rather than one.
The accessor clamps to the same bounds the slider carries,
LunaLib pruning nothing and handing a value left behind by an earlier spelling of a row to whatever later reads that ID.

**A retune is gated on the settings revision, and then on the value.**
`IntervalUtil.setInterval` draws a fresh interval and zeroes the elapsed time with it,
so a loop re-applying the reading it already held would reset its own timer every frame and never reach an interval's end.
The revision gate keeps the settings off the per-frame path;
the value gate is what makes re-applying safe at all,
since LunaLib announces that the settings changed rather than which one
and so every KMU knob the player moves arrives here.

**The revision is read rather than subscribed to**,
though `KmuLunaSettings` offers the callback and the mod's start-up wiring takes it.
A subscription cannot be withdrawn -
LunaLib's listener list only ever grows -
while a loop is built per watcher per sector and rebuilt on every load,
so subscribing would leave one listener per load standing for the life of the process.
A counter compared on a frame the loop is already being handed costs nothing beside that,
and goes away with the script.

## Two scripts, two classes

The loop is held by a script rather than being one,
and two scripts hold it:
`MapLayerSectorWatcher`,
installed per layer that has something to poll,
and `MapSubstrateSectorWatcher`,
installed once per sector.
They are two classes on purpose.
Vanilla's `removeTransientScriptsOfClass` compares `getClass() != clazz` -
exact identity,
no subtype -
and KMLib's `SectorScripts.installTransientScript` clears by the built script's own class before adding,
so a second instance of one class silently replaces the first,
and a layer taking its own poll back on a switch-off would take the substrate's with it.
A poll that must be installed and cleared on its own timetable needs an identity of its own,
and that identity is the whole of what each script class owns:
everything else -
the loop,
and the engine's three per-frame answers -
is `BaseStalenessSectorWatcher`'s.
A subtype is enough for that,
the class the engine is handed being the concrete one either way.

## The substrate's poll

`MapSubstrateStalenessSource` stales nothing.
It sweeps the sector for what each system's own inhabitants can see of the colonies a revelation gate holds back,
and writes that onto the shared sighting register (`SectorColonySightings`) -
a colony a gate holds back drops off the map the day its last neighbour dies
unless somebody wrote down that the neighbours saw it,
and nothing in the engine announces a derelict arriving among witnesses.
A poll is the shape that needs,
not a claim about any drawing,
so it raises on no board and holds no baseline.

It is the substrate's rather than a layer's
because the record is shared by every map family that keeps observations,
and a layer is something the player can take off the bar.
Accrual that followed one layer would have its gaps decided by an unrelated preference,
and nothing would report the gap -
what is lost is a colony's witnesses,
months later,
on a map the player has since put back.
`MapSubstrateRefreshInstaller` stands it up beside the machinery from the composition root,
so it runs for as long as the layers are standing at all.

The cost is a second reading of the sector's colonies,
where one served both while the sweep rode the political map's pass.
Taken knowingly,
and smaller than a whole pass:
the sweep asks each system only who lives there,
so it opens a bare colony index and never pays for the drawn-set scan a pass carries beside one.
A reading handed from the framework down to a layer would belong to the frame sequence rather than to either poll,
and the profiling scopes are what would say whether it is worth arranging.
