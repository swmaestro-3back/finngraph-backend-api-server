package com.finngraph.auth

import com.finngraph.auth.model.AuthProvider

class DuplicateCredentialException(val provider: AuthProvider) : RuntimeException("이미 등록된 자격증명: $provider")
