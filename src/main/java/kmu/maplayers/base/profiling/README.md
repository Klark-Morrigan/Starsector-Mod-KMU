# Map build profiling vocabulary

What the map layers say to the profiler beyond a section's name: the counters a rebuild's stages
add to, and the terms a rebuild step registers its section on.

## Index

- [Why a package](#why-a-package)
- [Counters](#counters)
- [Rebuild step terms](#rebuild-step-terms)

## Why a package

The stages that count run on both sides of the render: the cell cut and the label mint happen
before anything is painted, the hatch cut inside it. Geometry importing the render package to name
a counter would invert the one direction those two are related in, so the vocabulary the stages
share sits beneath both. The frame's beats stay with the frame sequence in
[the render surface](../render/README.md#what-a-frame-costs), being facts about it.

## Counters

`MapBuildCounters` holds three: `cells`, `labels` and `hatchSegments`. Every counter a reading's
rows touch adds two columns to it - the total and what one item cost - so what earns one is a volume
of work a duration is divided by. A detail of one call - the knobs a sweep ran under, how many of the
cells handed to a bake came back with a band, whether an update rebuilt anything at all - rides on
that call's tag, which the close line prints and the report keeps for the row's kept call. What the
sector holds is counted by the library's own walkers, so a stage's row already says how many systems
and markets it read without this naming any of them.

## Rebuild step terms

`RebuildStepTerms.LOGGED_EVERY_CALL` is what a rebuild step registers its section on: every call
writes its line, whatever it took. A rebuild happens when something changed rather than on a clock,
so each of its steps is an event a reader following the rebuild through the log wants to see - the
fast ones included. A step that should say more or less than the others changes its own
registration rather than this.

Stating any threshold at all also marks a section's calls as events rather than per-frame cost, and
the library reads such a call for whether it was its row's first and how far the JVM's compilation
clock moved under it (`first jitMs=412`). That is worth having on a rebuild step precisely because it
runs a handful of times a session: its first call is a large share of what a reader sees, and it runs
on code the JIT has not compiled yet. A per-frame beat states no threshold, so it is not read - the
bean is a native call, and a beat has warmed up long before anyone opens the readout.
