package org.xapps.services.models

import com.fasterxml.jackson.annotation.JsonProperty

data class Role(
    @JsonProperty("id")
    var id: Long? = 0,

    @JsonProperty("name")
    var name: String
) {
    companion object {
        const val ADMINISTRATOR = "Administrator"
        const val GUEST = "Guest"
    }
}