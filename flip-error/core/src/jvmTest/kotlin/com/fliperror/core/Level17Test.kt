package com.fliperror.core

class Level17Test : LevelGate(Level17.build()) {
    override val minBoosts = 2
    override val maxRest = 2.9
    override val starsMustBePayable = true
}
