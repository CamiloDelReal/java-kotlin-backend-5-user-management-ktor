package org.xapps.services.models

import com.fasterxml.jackson.annotation.JsonProperty

data class Authentication(
    @JsonProperty("token")
    val token: String,

    @JsonProperty("type")
    val type: String,

    @JsonProperty("expiration")
    val expiration: Long
)