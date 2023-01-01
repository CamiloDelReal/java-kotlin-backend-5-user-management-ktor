package org.xapps.services.models

import kotlinx.serialization.Serializable

@Serializable
data class Authentication(
    val token: String,
    val type: String,
    val expiration: Long
)