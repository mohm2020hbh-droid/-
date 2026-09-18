package com.fliperror.core

class Level15Test : LevelGate(Level15.build()) {
    override val minBoosts = 2
    override val maxRest = 3.0
    override val starsMustBePayable = true
}
