# Fixed-step physics and presentation

Physics time can remain independent of display cadence when the feature feeds
bounded elapsed time to a fixed-step solver and owns pause/resume baselines.

## Origin

The 2026-10-11 toss playground needed actual gravity, impacts and scoring rather
than a keyframed flight. Source inspection and both native numeric suites establish
this implementation's 30/60/120 Hz equivalence for the tested shots. This is a
design/measurement finding, not a universal numerical stability guarantee.

## What and why

The solver stores position/velocity in world units with +Y up. Each fixed step
first changes velocity by gravity × time, then position by the new velocity ×
time (semi-implicit Euler). Contact normals correct penetration and reflect the
approaching normal velocity; restitution dissipates its energy. The feature maps
positions into screen coordinates after stepping. Resizing the canvas changes
presentation, without changing the gravity or hoop geometry.

An accumulator converts a variable display interval into a bounded number of
120 Hz steps. This gives the same sequence of simulation steps across the tested
display cadences. A 100 ms admission cap avoids replaying an entire background
stall. Pausing is stronger: the feature stops calling the solver and obtains a
fresh time baseline on resume, leaving no background interval to admit.

## Example

Conceptual: a 60 Hz frame normally executes two 120 Hz steps; a 30 Hz frame
executes four. Both execute 120 steps over one simulated second. A ten-second
wall-clock stall admits only twelve steps; it deliberately loses elapsed time.
For predictable scoring, an aim preview advances a copy of the same solver.

## Gotchas

Discrete contact tests can tunnel at unbounded speeds. This world caps speed and
uses small steps; it does not promise continuous collision detection or a general
many-body solver. Interpolating a hoop-plane crossing improves the scoring test,
but the ball must still clear the finite rim geometry. An apparently centered
arc may clip the near rim before crossing the plane. A backboard rebound can
score normally. The net drawing does not imply deformable net physics.

CPU physics and GPU rendering are separate capabilities. A physically simulated
ball can use a simple native 2D drawing surface. A later renderer can consume
the same positions without becoming the simulation's clock or durable owner.
This slice measures no device FPS, thermal cost or production realism.

## Used in

Interactive toss targets, miniature games, educational forces/motion examples,
and bounded product interactions where a numeric model should survive a change
of renderer. The app/module walkthroughs provide source links.

## Related

- [Frame ownership](renderer-frame-ownership.md).
- [Graphics inputs and event time](graphics-inputs-and-event-time.md).
- [Tabs and feature lifetime](tabs-and-feature-lifetime.md).
