package com.fliperror.core

class Level27Test : LevelGate(Level27.build()) {
    override val minBoosts = 2
    override val maxRest = 2.0
    override val starsMustBePayable = true
}
