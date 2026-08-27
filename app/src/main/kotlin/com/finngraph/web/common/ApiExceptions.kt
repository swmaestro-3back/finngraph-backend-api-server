package com.finngraph.web.common

class ResourceNotFoundException(
    val code: String,
    message: String,
) : RuntimeException(message)

class InvalidParameterException(
    message: String,
    val fieldErrors: Map<String, String>,
) : RuntimeException(message)
