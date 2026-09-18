package com.fliperror.core

class Level14Test : LevelGate(Level14.build()) {
    override val minBoosts = 2
    override val maxRest = 3.1
    override val starsMustBePayable = true
}
