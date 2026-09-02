package com.finngraph.web.common

class ResourceNotFoundException(
    val code: String,
    message: String,
) : RuntimeException(message)

class InvalidParameterException(
    message: String,
    val fieldErrors: Map<String, String>,
) : RuntimeException(message)

class AuthenticationFailedException(
    val code: String,
    message: String,
) : RuntimeException(message)

class UpstreamUnavailableException(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
