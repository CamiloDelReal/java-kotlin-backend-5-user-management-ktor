package org.xapps.services.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    var id: Long = 0,
    var firstName: String,
    var lastName: String,
    var email: String,
    var password: String?,
    var roles: List<Role>? = null
)