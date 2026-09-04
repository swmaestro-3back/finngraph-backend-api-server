package com.finngraph.security

import com.finngraph.composition.port.PasswordHasherPort
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class SpringPasswordHasher(private val encoder: PasswordEncoder) : PasswordHasherPort {

    override fun hash(raw: String): String =
        checkNotNull(encoder.encode(raw)) { "PasswordEncoder가 해시를 생성하지 못했습니다." }

    override fun matches(raw: String, hash: String): Boolean = encoder.matches(raw, hash)
}
