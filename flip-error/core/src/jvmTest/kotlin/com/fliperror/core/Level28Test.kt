package com.fliperror.core

class Level28Test : LevelGate(Level28.build()) {
    override val minBoosts = 2
    override val maxRest = 2.5
    override val starsMustBePayable = true
}
