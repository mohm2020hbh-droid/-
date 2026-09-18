package com.fliperror.core

class Level13Test : LevelGate(Level13.build()) {
    override val minBoosts = 2
    override val maxRest = 3.2
    override val starsMustBePayable = true
}
