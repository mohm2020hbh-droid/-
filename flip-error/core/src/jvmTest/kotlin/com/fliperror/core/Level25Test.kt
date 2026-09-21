package com.fliperror.core

class Level25Test : LevelGate(Level25.build()) {
    override val minBoosts = 2
    override val maxRest = 2.1
    override val starsMustBePayable = true
}
