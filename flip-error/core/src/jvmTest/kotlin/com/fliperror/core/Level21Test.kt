package com.fliperror.core

class Level21Test : LevelGate(Level21.build()) {
    override val minBoosts = 5
    override val maxRest = 2.5
    override val starsMustBePayable = true
}
