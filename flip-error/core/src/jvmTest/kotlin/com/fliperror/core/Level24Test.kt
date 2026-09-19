package com.fliperror.core

class Level24Test : LevelGate(Level24.build()) {
    override val minBoosts = 5
    override val maxRest = 2.6
    override val starsMustBePayable = true
}
