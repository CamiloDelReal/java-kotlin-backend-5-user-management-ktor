package org.xapps.services.models

import com.fasterxml.jackson.annotation.JsonProperty

data class User(
    @JsonProperty("id")
    var id: Long,

    @JsonProperty("firstName")
    var firstName: String,

    @JsonProperty("lastName")
    var lastName: String,

    @JsonProperty("email")
    var email: String,

    @JsonProperty(value = "password", access = JsonProperty.Access.WRITE_ONLY)
    var password: String?,

    @JsonProperty("roles")
    var roles: List<Role>?
)