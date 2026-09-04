package com.finngraph.composition.port

interface PasswordHasherPort {
    fun hash(raw: String): String
    fun matches(raw: String, hash: String): Boolean
}
