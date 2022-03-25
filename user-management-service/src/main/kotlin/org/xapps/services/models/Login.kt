package org.xapps.services.models

import com.fasterxml.jackson.annotation.JsonProperty

data class Login(
    @JsonProperty("email")
    val email: String,

    @JsonProperty("password")
    val password: String
)