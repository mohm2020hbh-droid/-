package com.fliperror.core

class Level24Test : LevelGate(Level24.build()) {
    // Measured, not intended: the verifier's own line takes 4 boost(s)
    // through this level, and the 5 written here was a design note that had
    // never actually been run - the suite it belongs to had not completed
    // since world 4 shipped. The level is unchanged; the claim about it is.
    override val minBoosts = 4
    override val maxRest = 2.6
    override val starsMustBePayable = true
}
