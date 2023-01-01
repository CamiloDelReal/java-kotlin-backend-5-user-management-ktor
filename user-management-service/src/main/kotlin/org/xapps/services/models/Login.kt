package org.xapps.services.models

import kotlinx.serialization.Serializable

@Serializable
data class Login(
    val email: String,
    val password: String
)