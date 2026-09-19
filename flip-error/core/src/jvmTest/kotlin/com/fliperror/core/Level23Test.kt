package com.fliperror.core

class Level23Test : LevelGate(Level23.build()) {
    override val minBoosts = 4
    override val maxRest = 2.6
    override val starsMustBePayable = true
}
