package com.fliperror.core

class Level26Test : LevelGate(Level26.build()) {
    override val minBoosts = 2
    override val maxRest = 2.1
    override val starsMustBePayable = true
}
