# Map build profiling vocabulary

What the map layers say to the profiler beyond a section's name:
the counters a rebuild's stages add to,
the terms a rebuild step registers its section on,
and the one bound that arrives from outside the framework.
Below them,
what is measured at all and what the readout writes.

Part of [the map layers](../../README.md),
in Klark Morrigan's Utilities (KMU);
see the [mod README](../../../../../../../README.md) for project context.

## Index

- [Why a package](#why-a-package)
- [Counters](#counters)
- [Rebuild step terms](#rebuild-step-terms)
- [The frame beat bound](#the-frame-beat-bound)
- [What is measured](#what-is-measured)
- [The readout](#the-readout)

## Why a package

The stages that count run on both sides of the render:
the cell cut and the label mint happen before anything is painted,
the hatch cut inside it.
Geometry importing the render package to name a counter would invert the one direction those two are related in,
so the vocabulary the stages share sits beneath both.
The frame's beats stay with the frame sequence in [the render surface](../render/README.md#what-a-frame-costs),
being facts about it.

## Counters

`MapBuildCounters` holds three:
`cells`,
`labels` and `hatchSegments`.
Every counter a reading's rows touch adds two columns to it -
the total and what one item cost -
so what earns one is a volume of work a duration is divided by.
A detail of one call -
the knobs a sweep ran under,
how many of the cells handed to a bake came back with a band,
whether an update rebuilt anything at all -
rides on that call's tag,
which the close line prints and the report keeps for the row's kept call.
What the sector holds is counted by the library's own walkers,
so a stage's row already says how many systems and markets it read without this naming any of them.

## Rebuild step terms

`RebuildStepTerms.LOGGED_EVERY_CALL` is what a rebuild step registers its section on:
every call writes its line,
whatever it took.
A rebuild happens when something changed rather than on a clock,
so each of its steps is an event a reader following the rebuild through the log wants to see -
the fast ones included.
A step that should say more or less than the others changes its own registration rather than this.

Each call's line carries the duration the row was accumulated from,
what the call counted,
and what it named itself.
So a step carries one measurement,
read in the log as a rebuild happens or in the report afterwards,
rather than a hand-written log line beside the row that could disagree with it.
The lines follow the profiler,
which is the price of that single measurement:
with the level knob off,
a rebuild says nothing at all,
even with the log at debug.

Stating any threshold at all also marks a section's calls as events rather than per-frame cost,
and the library reads such a call for whether it was its row's first
and how far the JVM's compilation clock moved under it (`first jitMs=412`).
That is worth having on a rebuild step precisely because it runs a handful of times a session:
its first call is a large share of what a reader sees,
and it runs on code the JIT has not compiled yet.
A per-frame beat states no threshold,
so it is not read -
the bean is a native call,
and a beat has warmed up long before anyone opens the readout.

## The frame beat bound

`MapFrameBudgets` holds how long one beat of a map frame may take,
and is the one thing here the framework does not decide for itself:
a composition root binds where the number comes from,
and [the frame sequence](../render/README.md#what-a-frame-costs) reads it back as a plain double.

Inverted rather than read from the settings at the beat,
because of what sits beside that row.
The knob deciding how much of the framework is measured at all is in the same `Map - Dev` Profiling section,
and a file under the framework naming the class that holds one would have the other within reach -
code able to see whether it is being watched can act on it,
which is worth more than the convenience of a direct read.
Bound this way,
nothing under `kmu.maplayers.base` names a settings class.

Unbound answers no bound at all rather than a default restated here.
What the shipped number is belongs to the settings row and the accessor that answers with it;
a copy would be a second place to correct
and could disagree with the row while looking authoritative.
Nothing is lost by it -
a bound is only ever consulted while a capture is running,
and the same root binds both.

## What is measured

Nothing is recorded unless a profiler is bound:
the library's default keeps nothing and allocates nothing,
so an unmeasured frame pays a virtual call per beat and no more.
Which one is bound follows the `Profiling level` knob on `Map - Dev`,
which ships off,
so a player who never opens the readout is not measured at all.
The frame's beats are coarse,
so any running capture times them;
what a level rules out is the finer work inside a per-item loop.

## The readout

The `kmu_profiling` console command writes a reading of the capture rather than the capture itself:

- `tree` is the beats as they were measured,
  each row under the one it ran inside;
- `flat` puts the rows that spent the time first,
  each named by its whole path;
- `walks` keeps only the rows that traversed the sector,
  worst call first,
  which is the witness the refresh's one-walk bound is stated against.

`namespace=` narrows any of them to one layer's rows,
keeping the beats those rows sit inside so a share is still readable against the whole,
and `top=` keeps only as many rows as are wanted.

`perframe` divides the totals by the calls of `mapLayer.prepare`,
the beat that runs once a frame,
so a figure means the same whether the map was left open for four seconds or four minutes.
A capture holding no call of it says so and leaves the totals alone.

Every reading goes to `starsector-core/starsector.log`,
and the console gets a line saying so.
The table is wider than the console overlay can lay out,
and the log is the file a capture asked for in a bug report has to end up in anyway.
The notice is printed rather than nothing,
since a command that visibly did nothing reads as one that failed.

The beats themselves,
and the bounds they are held to,
are [the frame sequence](../render/README.md#what-a-frame-costs)'s.
