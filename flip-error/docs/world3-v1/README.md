# WORLD 3, first version — kept, not used

These are the files the abyss shipped as before it was rebuilt, copied here
verbatim and given a `.bak` suffix so nothing compiles them. Git has them too,
at commit `680f778`; this directory exists because a backup you have to know a
commit hash to find is not a backup anyone will find.

| file | what it was |
|---|---|
| `Abyss.kt.bak` | the first kit: chasing/split bubbles, abyss and hunting orbs, current bursts, rising and descending walls, light corridors, rotating tunnels, electric currents, tentacles that swept sideways, bubble floors, bursting bridges, mines |
| `Level13..18.kt.bak` | DEEP SIGNAL, SPLIT, PRESSURE, THE ARMS, MEMORY, THE SUN BELOW |
| `Level13..18Test.kt.bak` | their gates |

## Why it was replaced

It was built around a different question — "whether to jump at all", answered by
slabs dropping through the air a jump would use — and it worked. What it was
not was a WATER world. Its obstacles were walls, slabs, corridors and mines: the
city's vocabulary at a different angle, in a blue room.

Two things about it are worth keeping in mind rather than repeating:

**It read as dense and played as idle.** Its levels measured take-off windows
barely tighter than world 2's while leaving the player alone for as long as 6.11
seconds at a stretch. `LevelGate.maxRest` exists because of that, and the
rebuild holds every abyss level under about two and a half seconds.

**Four of its obstacles were never placed in a level.** `rotatingTunnel`,
`huntingOrb`, `bubbleFloor` and `risingWall` existed in the kit, compiled, and
appeared nowhere a player could meet them. A kit is not a world.
