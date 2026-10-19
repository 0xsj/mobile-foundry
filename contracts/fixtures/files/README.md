# Files fixture

`photo.png` is an owned, deterministic 48 × 48 RGB test pattern, generated from
PNG scanlines with Python's standard-library `struct` and `zlib`. It contains no
personal photo or external asset. The generated acceptance runner stages a copy
into the simulator/emulator gallery and drives the actual native photo picker.
Backend seeds use the identical bytes; uploads still calculate their own digest.
