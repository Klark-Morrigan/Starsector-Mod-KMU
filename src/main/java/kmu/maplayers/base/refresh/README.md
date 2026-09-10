# Refresh (`maplayers/base/refresh`)

What says a cached overlay has gone stale, and the throttled polls that find the changes the engine
announces to nobody. Which signals exist, who declares which, and the four rebuild paths they drive
are [the caching notes](../../../../../../../docs/dev/caching.md)'; this register owns the poll shape
and the one poll that is nobody's layer.

Part of [the map layers framework](../../README.md).

## Index

- [The signals and the board](#the-signals-and-the-board)
- [The poll](#the-poll)
- [Two scripts, two classes](#two-scripts-two-classes)
- [The substrate's poll](#the-substrates-poll)

## The signals and the board

A signal is a `MapLayerRefreshSignal`, declared by the framework (`MapLayerCommonRefreshSignal`) or by
the layer that alone means anything by it. `MapLayerRefreshBoard` is what a signal is raised on, one
per sector held by [that sector's installation](../installation/README.md), since the stale set names
systems by bare id. Every producer is handed the board it means, and this package is gated from
importing the installation one so none can resolve a board of its own. `RefreshSignalRevisions` is a
reading of where the traced signals stood, for a cache to name what moved since it last rebuilt.

## The poll

Some changes come with no engine event at all - a gate activating, a system cut off, a colony founded
in a system already drawn - so the only way to find them is to re-read the sector on a cadence and
diff it against the last read. `StalenessPollLoop` is that cadence: a throttle of a few campaign
seconds, a fault guard that says once per session which source faulted and keeps polling, and the
call into a `MapLayerStalenessSource`. What to re-read, what counts as a change and which signal each
change earns are the source's answers, so the loop names no layer and no signal.

`MovingSystems` rides the political map's poll as a passenger: it stages which drawn systems rewrite
their own hyperspace position, so the geometry leaves a mover out of the partition rather than chase
it.

## Two scripts, two classes

The loop is held by a script rather than being one, and two scripts hold it: `MapLayerSectorWatcher`,
installed per layer that has something to poll, and `MapSubstrateSectorWatcher`, installed once per
sector. They are two classes on purpose. Vanilla's `removeTransientScriptsOfClass` compares
`getClass() != clazz` - exact identity, no subtype - and KMLib's `SectorScripts.installTransientScript` clears by the
built script's own class before adding, so a second instance of one class silently replaces the
first, and a layer taking its own poll back on a switch-off would take the substrate's with it. A
poll that must be installed and cleared on its own timetable needs an identity of its own, and that
identity is the whole of what each script class owns.

## The substrate's poll

`MapSubstrateStalenessSource` stales nothing. It sweeps the sector for what each system's own
inhabitants can see of the colonies a revelation gate holds back, and writes that onto the shared
sighting register (`SectorColonySightings`) - a colony a gate holds back drops off the map the day
its last neighbour dies unless somebody wrote down that the neighbours saw it, and nothing in the
engine announces a derelict arriving among witnesses. A poll is the shape that needs, not a claim
about any drawing, so it raises on no board and holds no baseline.

It is the substrate's rather than a layer's because the record is shared by every map family that
keeps observations, and a layer is something the player can take off the bar. Accrual that followed
one layer would have its gaps decided by an unrelated preference, and nothing would report the gap -
what is lost is a colony's witnesses, months later, on a map the player has since put back.
`MapSubstrateRefreshInstaller` stands it up beside the machinery from the composition root, so it
runs for as long as the layers are standing at all.

The cost is a second reading of the sector's colonies, where one served both while the sweep rode
the political map's pass. Taken knowingly, and smaller than a whole pass: the sweep asks each
system only who lives there, so it opens a bare colony index and never pays for the drawn-set scan
a pass carries beside one. A reading handed from the framework down to a layer would belong to the
frame sequence rather than to either poll, and the profiling scopes are what would say whether it is
worth arranging.
