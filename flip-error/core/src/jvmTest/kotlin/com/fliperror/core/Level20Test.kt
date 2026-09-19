package com.fliperror.core

class Level20Test : LevelGate(Level20.build()) {
    override val minBoosts = 4
    override val maxRest = 2.6
    override val starsMustBePayable = true
}
