package com.fliperror.core

class Level16Test : LevelGate(Level16.build()) {
    override val minBoosts = 2
    override val maxRest = 3.0
    override val starsMustBePayable = true
}
