package com.fliperror.core

class Level18Test : LevelGate(Level18.build()) {
    override val minBoosts = 2
    override val maxRest = 2.9
    override val starsMustBePayable = true
}
