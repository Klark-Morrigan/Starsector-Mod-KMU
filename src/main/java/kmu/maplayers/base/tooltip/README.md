# The hover box (`maplayers/base/tooltip`)

The box floating beside the cursor,
in a later UI pass than the map's own.

Part of [the map layers framework](../../README.md).

## Index

- [The four divisions](#the-four-divisions)
- [The pass, and the gates](#the-pass-and-the-gates)
- [The detail level](#the-detail-level)
- [The box a layer's account takes](#the-box-a-layers-account-takes)
- [What a line says](#what-a-line-says)
- [Laying out, and fitting](#laying-out-and-fitting)
- [Marks](#marks)

## The four divisions

It divides in the order a box is arrived at,
and the divisions are gated
([package-layering.gradle](../../../../../../../gradle/package-layering.gradle)) rather than left to review:
**`content`** is what a layer states -
a line,
its mark,
its status,
its place in an ordering
- and imports none of the rest,
  so what a line says can be stated without knowing which tier it lands at,
  what colour that tier speaks in,
  or whether the box has room for it;
  **`detail`** is how deep the player asked the box to read,
  as a value and the held choice behind it,
  and knows nothing of either;
  **`layout`** is how a statement becomes a box -
  the blocks,
  the line vocabulary,
  the cut,
  the fit,
  the look,
  the line at the foot and the box itself;
  and the root is the plumbing that decides which box is drawn at all,
  which reaches into neither of the two ends.

## The pass, and the gates

That pass is `CampaignUIRenderingListener`'s,
which the engine drives for the whole campaign UI rather than for a map screen -
so the box has a host wherever the campaign is drawn,
game space included,
and reaching a docked map surface cost a gate rather than a render host.
`MapLayerCellTooltip` owns the gates every hover box shares
(the settings tiers above any layer, a frame the cursor can be located against, stepping aside for the vanilla star tooltip) and draws whichever `MapHoverTooltip` the active layer's renderer injects -
so a layer with none,
a layer whose own tooltip switch is off,
and a switch-only tab with no renderer at all show nothing for the same reason.
The step-aside is rooted at whichever surface owns the frame,
`ShownMapSurface` in `kmu.starsector.ui` -
a tab-rooted walk cannot reach a docked map's tooltip,
those being up on exactly the frames no tab is.

Both passes -
the one that reads the detail key and the one that draws its result -
read one gate seam,
`HoverTooltipGates`
(the settings tiers above any layer, and a map on screen) rather than a copy each,
so the key is claimed when and only when a box could be drawn
and a condition added later reaches both passes.
That map gate is host-blind and look-blind (KMLib's `MapPresence.isAnyMapShowing`),
so the box draws wherever the layer paints:
the sector map and the intel screen's map visor,
in the schematic look and in Starscape alike.
Which is what the listener needs,
being called for the whole campaign UI and never told which screen is up.
What the cursor is over is resolved once,
by `HoveredBox`,
and read by both passes -
a chain spelled out twice is one edit away from the key acting on a frame the box does not draw.

## The detail level

How much detail the drawn box states is one shared fact rather than a per-layer one:
`HoverTooltipDetailLevelState` carries the ordered `HoverTooltipDetailLevel` -
four depths of the same account,
from factions alone down to the patrol split -
and the dispatcher hands it to the box it draws (`MapHoverTooltip.renderFor`),
which reads its own content only that deep.
A level is a cut rather than a choice of body:
a layer composes one tree and the blocks lay out as much of it as the level admits,
so four depths cost no layer a second account of a system that could come to disagree with the first.
What is *drawn* is the cut's alone,
and that is what lets a layer stop composing a tier the level would drop -
which the political map does,
its deeper tiers being the expensive ones,
so the shallowest level walks no colony of the hovered system at all.

The level answers two questions for that:
`isAdmittingAccounts`,
whether anything hangs beneath a listed line at all -
answered off what an account *is*,
one step under the box's own voice,
rather than off the level that happens to be the first to admit one,
so a level inserted between two of these keeps answering about the right thing -
and `isReadingAtLeast`,
asked by a layer about a tier of its own subject matter.
The cut answers a tier it was never handed exactly as it answers one it declines.
The level never decides *which* box draws either -
one box per layer,
read to as many depths as its account holds -
so the choice holds across hovers and layer switches without any layer holding a second body.

What writes that level is `HoverTooltipDetailLevelInput`,
a campaign input listener claiming F1 pre-core:
each press moves one level deeper,
wrapping back to the first,
so the key that leads into detail also leads out of it.
Where it wraps is the *box's* depth rather than the last constant,
for the reason `HoverTooltipDetailLevel` sets out:
the levels name tiers of one particular account,
and a box built on another mechanic has none of the deeper ones to fill.
A render pass is handed no events and so can consume none,
which is why reading the key and drawing its result are two passes agreeing through the holder.

Behind the gate seam the press is claimed only where it would do something the player can see:
the box under the cursor answers `MapHoverTooltip.resolveNextLevelFor` for the hovered system at the level being drawn,
and a system with nothing more to state names nowhere and leaves the key alone.
Answered as the destination rather than as a yes or no,
because the listener has to set the level it lands on and cannot work that out itself -
only the box knows where its own tree ends.
Asked per system rather than per box because part of the answer lives there -
an unpopulated system has no colonies for a deeper tier to account for;
asked at a level because the rest of it lives there -
what the press has to change is the depth the box is *cut* at rather than the level it names,
and a box past its own bound is cut at that bound whatever level is being read.
So a box with nothing below the shallowest level offers nothing from any level at all,
and the way back out of a deep one is any box that has depth to collapse -
a level nothing here draws is a level nothing here has to escape.
Left unclaimed rather than advanced invisibly because the level is one shared fact:
a press swallowed over a system with nothing to expand would silently decide how the next system that *does* differ opens.
Vanilla keeps F1 everywhere else,
and the listener runs below the sidebar's
so a tab hotkey keeps the first claim on any key it is bound to.

## The box a layer's account takes

`SystemCellTooltip` is the shape a layer's box takes -
the hovered system's name over the layer's own content,
one draw for both.
The box opens with a heading block -
the system name and any title lines read on from it -
and the layer's own blocks follow beneath,
so a verdict that settles the whole system heads the box while a status or an entry sits in it.
It is the shape alone:
how the box is *set* is `CellTooltipLook` and the line it *ends* on is `CellTooltipFooter`,
each answering a question of its own,
so a layer changing none of them cannot be affected by either.

`CellTooltipLook` is one look for every layer -
the name in the game's own title face over body-face rows,
so a KM hover reads as part of the interface rather than as text laid over it -
and it is built per paint rather than settled once,
half of it being the player's:
the density knobs are read live,
so a slider moved with the map open takes effect on the next frame.

A box taking part in the detail cycle ends on `CellTooltipFooter`'s one more block:
the key and what pressing it would do,
drawn the way the game draws its own key hints -
the key picked out in the shade vanilla highlights a shortcut with,
the words about it in vanilla's grey,
in vanilla's own smaller condensed face.
What it says is the step the *next* press takes -
"expand market stats",
and "collapse to factions" once the box's own tree runs out -
rather than which level is current,
a number or a name telling the player nothing about what they would gain.
The phrase is the level's
(`HoverTooltipDetailLevel.resolveArrivalPhrase`),
carried by the level being arrived at,
so every layer names one step the same way and a level added brings its own wording with it.

What a layer answers is how deep it goes for this system,
and it comes back beside the blocks rather than being asked for (`ComposedCellBody`):
that turns on what the body found,
so a layer that read its system to compose the blocks already holds the answer.
Asked separately,
the box would pay for that read a second time every frame the cursor rests on the cell -
and the hint could describe a reading the body beside it no longer agrees with.
The key handler asks through `resolveDeepestHeldLevelFor` instead,
that being the one caller with nothing composed to take the answer from,
and it asks once per press,
at every level:
the bound is what says whether a press would show the player anything,
so no level settles that question without it.
The bound is two facts,
and a layer joins them in one place:
how far the box's *account* reaches,
a constant of what it explains,
and whether this system left it anything to account for at all.
Both the hint and the key handler go through one `CellTooltipFooter.resolveOfferedLevel`,
so the box neither advertises a key that does nothing nor claims one it said nothing about.
The hint is not content:
a box with nothing to say about the system stays undrawn
rather than appearing as a lone offer to expand into nothing.
That same line carries one more run where the box had less room than its content needed:
how many entries it could not show,
in the quiet shade the box states everything about its own account in.
It is what stops a cut box reading as a complete one over the whole of it,
the rows standing in for withheld entries saying the same thing listing by listing.

How far apart those blocks stand is never a line's own request:
KMLib parts one block from the next by one measurement,
and a listing nested inside a block by a narrower one,
so what sets two things apart is what they are rather than which line happens to open them.

## What a line says

A layer states only *what* each block lists,
as `CellTooltipEntry` / `CellTooltipEntryLine` values -
an entry being a line over the entries beneath it,
so how deep a listing goes follows the subject matter rather than the model.
What those entries are to it is stated too,
since depth alone cannot say:
`grouping` gathers peers -
an alliance and the factions in it are one answer at two granularities -
while `nesting` carries the account of why the line above reads as it does.
Both sit inset;
only the second stands a step further under the box's voice,
so an allied holder's markets read exactly as loudly as a lone holder's instead of being demoted by a level the account had nothing to do with.
The demotion buys two things.
A size:
`SystemCellTooltip` asks KMLib for a fixed step per level,
so a listing several levels deep gives the eye a cue agreeing with its indent,
down to a floor the widget stops at.
Asked for on the shared box rather than on the one layer that first listed anything that deep,
since two layers demoting a line by different amounts is a difference a reader has no way to account for.
And the detail cut:
the level admits a line by its demotion alone (`CellTooltipEntryLevel.isAdmittedBy`),
so an alliance's member factions survive the shallowest level
- being the very content that level exists to show -
  while the markets beneath either of them do not.
  Cut on the indent instead,
  a listing would lose exactly what it was asked for.

A line's number may itself be two things -
a finding and the working it came out of,
such as the rate one of a counted thing is worth over what the count came to.
The line states the halves apart (`derivesValueFrom`)
and the vocabulary greys the working against whatever colour the line speaks in,
exactly as it golds a qualifier:
run together in one string they could only be drawn in one shade,
and the reader would take a value joined by a separator for a single number.

Two further readings take that same quiet shade,
and they are deliberately different sizes.
`readsAsAside` says the whole line is a note *about* the list rather than one of the things in it -
the arithmetic of a term its members share,
stated once beneath them -
so it quietens down to its name and only the number it arrives at stays a finding.
`statesUncountedValue` is the narrower one:
the line *is* one of the things listed and is named as loudly as its neighbours,
but its number is one nothing earned -
what an account recorded for it rather than anything it did.
Drawn as loudly as the numbers around it,
such a nought invites the one comparison it cannot bear.

A line may also remark on the thing it names (`notedWith`) -
something that is not a finding about it,
such as how current what the box says about it is.
That run takes the same quiet shade for the same reason the working does:
the box parts what it has found from what it is saying about its own account,
and a remark drawn in the qualifier's gold would invite the reader to weigh it against the numbers on the line
rather than against the line's standing.
It closes the line,
past the qualifier,
being the only run that is not about the thing on the line:
set ahead of the gold,
it would break a status away from the name it qualifies.

A status is itself a small value (`CellTooltipQualifier`):
the finding it always states,
plus -
for a line calling out the thing it *belongs to* rather than something about it -
a word introducing that finding,
a mark of what the finding names,
and a word closing it with what kind of thing that is.
One value rather than parts layered on separately,
since applied apart they leave a line free to end on a connective introducing nothing or on a picture of something it never names.
Only the finding is gold
- the words either side are the box's own,
  and the closing one is a category rather than a name -
  so the plain status nearly every line carries (`qualifiedWith`) stays the single gold run it has always been.

A finding may also sit inside the line's own name (`callsOutInLabel`),
as a `CellTooltipLabelFinding`
- the stretch of the label that says it,
  held as character positions since the name is the only copy of the name.
  It is drawn in the qualifier's gold where it stands,
  so a thing named after what it is states that finding
  once instead of ending on a word its name already carries.
  The stretches either side of it are joined runs (`LabelRun.isJoinedToPreviousRun`),
  which is what keeps the split name spelled as its author spelled it.
  At most one stretch per line:
  a second would cost the line model a list of parts where one part does.

A line may instead withhold its name outright (`createRedactedLine`),
carrying the shape of it as KMLib's `RedactedSpan` in place of the words.
Such a line is listed rather than left out,
so whatever the thing contributed to the block's arithmetic is accounted for on a line of its own instead of surfacing as a difference nothing explains,
and the redaction takes the whole of the name's place -
the mark still opens the line and the place,
status and remark run on after it exactly as they do elsewhere,
so it reads as one of the list with a part blocked out rather than as a shape of its own.
A separate factory rather than a refinement,
and word lengths rather than the name:
what the line must not show never reaches it,
so no later change is in a position to draw it.
The two accounts of what a line is called are exclusive at construction -
said or withheld,
never both -
and a line withholding its name cannot gild a stretch of it,
there being no letters to match.

A line may also state where it falls in an ordering (`indexedAt`),
as a `CellTooltipIndexPlace` -
the number the reader sees and,
as one value with it,
what that place decided (`CellTooltipIndexOutcome`).
It runs on after the name in the quiet shade,
ahead of any qualifier,
because it identifies the line rather than saying anything about it.
Where the ordering actually settled something between two otherwise-equal lines the place stops being an identifier
and reads in vanilla's positive or negative shade instead,
since at that moment the number *is* the reason one line beat another.
Text and outcome travel as one value because neither is separately true,
and carried apart they could drift -
an outcome left behind by a re-numbered place would mark the wrong line as having won.

A line whose number is one the block's own arithmetic adds up states that number rather than words for it
(`createCountedLine`, and `createRedactedCountedLine` where the name is withheld),
and the line words it.
That is what lets a listing be stood for when there is no room to draw it whole:
a row saying how much was left out can only sum lines that carry the figure they show,
and a count passed in beside separately-worded text would be free to disagree with it.

## Laying out, and fitting

`CellTooltipBody` is the body under construction,
appended to block by block:
a heading over what it lists,
or a banner listing nothing.
It holds both the running order and the depth because every block needs both -
a layer that stated them per block could append one to the wrong list or hand four blocks a level and the fifth another,
and a box that is two depths at once is a state the player cannot ask for -
and it drops a block that resolved empty.

What it comes to is `CellTooltipBlocks`,
and that is what a layer hands back.
The blocks stay blocks rather than becoming lines because a body is laid out more than once:
what a system holds decides how tall the box is and only the screen decides what fits,
which is known after the reading rather than during it.
Laying out walks the entries depth-first,
each becoming a nested block of its own line over its account -
which is what lets KMLib set one entry's whole breakdown apart from the next entry at its tier
rather than from its last line -
carrying the indent and the demotion as one `CellTooltipEntryLevel`.

Two cuts are spent in that walk and they answer different questions.
The detail level is the player's standing choice,
applied the same way over every system,
and what it leaves out is not reported -
the hint at the foot already offers it back.
The entry allowance is the box's answer to one system being too large for the screen,
so what *it* leaves out is stated:
the tail of each listing goes,
which is its low-scoring end,
and one `WithheldEntriesLine` row closes the listing with how many entries stand behind it
and what they came to between them.
At least the first entry of every listing survives,
a heading over nothing being a block that failed to fill rather than a box short of room.
The allowance is spent at every depth,
since a box runs long by depth as much as by breadth:
reaching only the blocks' own entries,
it would drop whole factions while leaving every term of the one that survived.

Which of the two a box reaches for is `CellTooltipContentFit`'s order,
and it is size first,
content last.
A tooltip takes no input,
so nothing it leaves out can be scrolled back to -
which makes every line worth keeping at a smaller size than it is worth dropping.
So the box is measured against the screen,
compressed toward its deepest line by KMLib's `TooltipHeightFit` where that brings it inside,
and only where the floor that compression stops at is still too tall is an allowance solved for.
The gentlest answer wins at both steps:
the cut box is compressed afresh from the authored look,
being a smaller box than the one that needed the floor,
and no more entries are given up than the room requires.
A box that fits -
which is nearly every box -
is drawn exactly as it was composed.

`CellTooltipRows` is the line vocabulary it lays them in,
which reads the tier and the indent off that level rather than off a choice the block makes,
plus the banner centred under the title.
Everything a listed line *says* -
its mark,
its name picked apart where the name itself says a finding,
its place in an ordering,
what it calls out and what it remarks -
is `CellTooltipLabels`,
arriving at the row as the runs of one label.
The seam is says against sits:
the row decides where the line lands,
how loudly it speaks and what fills its value column,
and nothing else.
The label is handed the tier's colour rather than choosing one,
and hands back runs rather than a row,
so nothing about what a line says commits it to the shape it says it on.
Only the findings read gold
(`CellTooltipLabels.buildFindingSpan`, which the banner's public `buildQualifierSpan` is the outward face of);
a place identifies the line,
a word introducing a status is the box's own connective,
and a remark is the box talking about its own account,
so all three stay quiet and a reader scanning for findings passes over them.

## Marks

A mark travels as a run at the head of the line carrying it on every shape,
never in a leading column,
so every line opens at the box's content edge and the indent alone says how deep a line sits:
a column is one gutter shared down a flat stack,
and a listing four levels deep would draw a mark several levels in inside the gutter the shallowest marked line widened,
well left of the name it belongs to.
What colour a mark draws in follows from what the mark is for,
which the mark itself states
(`CellTooltipMark.isInLineColour`, set by whichever of its factories composed it):
a glyph standing in for the name beside it takes that name's own tier colour
(`resolveMarkInLineColour`, or `resolveMarkForMapIcon` where the glyph is the one the sector map marks an entity by),
so the two read as one thing,
while a crest is a picture in its own right
and keeps the colours of its own pixels (`resolveMarkAsAuthored`).
Which of the two a mark is cannot be read off the sprite -
the same artwork could be either -
so whatever composes the line says it where the path is named,
and the mark names no colour itself:
which shade a tier speaks in is the block's to settle,
and an asset authored to carry across the sector map is the loudest run on a line
whose meaning is in the words.
Absence is a null mark rather than a mark with nothing to load,
and every factory answers it,
so a caller resolving a mark it may not have never branches first.
Its table shapes are the block's alone,
so a body cannot author a look of its own.
So two layers' boxes differ only in what they say.
