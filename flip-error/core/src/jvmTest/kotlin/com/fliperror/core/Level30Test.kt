package com.fliperror.core

class Level30Test : LevelGate(Level30.build()) {
    override val minBoosts = 2
    override val maxRest = 2.1
    override val starsMustBePayable = true
}
