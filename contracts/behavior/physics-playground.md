# Toss physics and playground

The reusable native Physics capability owns a finite one-ball 2D world. The
catalog owns gestures, controls, drawing, frame scheduling and session lifetime.
Positions use metres with +Y up in a 10 × 7 world; presentation maps this space
to a fixed-aspect canvas without changing collision geometry when resized.

Launch is accepted only while Ready, for a finite velocity of at least 0.2 m/s.
Speed is capped at 14 m/s. Drag displacement maps to velocity with gain 2.8;
angle/power controls invoke the same launch method. Repeated launch while Flying
or Settled is refused. New throw restores the ball while retaining session totals.
Changing targets clears totals; changing gravity/bounce retains totals and resets
the ball. Gravity is admitted in 4…20 m/s², restitution in 0…0.9, with finite defaults.

Motion uses semi-implicit Euler at 120 Hz with an accumulated elapsed time.
One call admits at most 100 ms and executes at most twelve steps; excess wall
time is discarded rather than replayed. Invalid/nonpositive elapsed time does
nothing. This is a bounded discrete solver, not continuous collision detection.
Walls, floor, two circular rim ends and a capsule backboard correct penetration
and reflect approaching normal velocity using restitution. Ground friction and
small-impact sleep dissipate motion. A throw settles after 300 ms at rest or a
12-second simulation limit. There is no angular/spin, air-drag, deformable net,
many-body contact solver or 3D scene in this slice.

Hoop scoring requires a downward crossing of the rim plane through a gap that
fits the whole ball; an upward crossing or overlapping rim contact does not
score. Crossing X is interpolated between step positions. The landing zone
scores only the first floor contact and requires the whole ball inside its
horizontal bounds. Each throw scores at most once. The net is visual only.
Aim preview executes a copy through the same solver and cannot change live state.

The canvas provides a bounded trail, preview and both target geometries. Only
gestures starting near the Ready ball are admitted. Release launches; cancelled
gestures do not launch. Native angle/power sliders and Launch ball provide an
alternative to precision dragging. Pause/New throw remain reachable by scrolling.

The feature advances only while present, foreground and manually unpaused. A
resume starts from a fresh clock baseline, so background time is not simulated.
Leaving the screen cancels its loop. Fresh screen instances and Android Activity
recreation start new playground sessions; durable restoration is not promised.
User-requested throws remain available with reduced motion; no automatic
decorative motion or score animation is introduced.

The catalog links the actual reusable Swift package/Kotlin module. This capability
has not been registered or independently accepted as a CLI feature combination.
Device performance and physical fidelity beyond this bounded 2D model remain
separate work. [Shared shots](../fixtures/physics/shots.json) cover a swish, rim
miss, ground target and ground miss; native suites supplement them with contacts,
elapsed budgets, frame cadence and world-bound checks.
