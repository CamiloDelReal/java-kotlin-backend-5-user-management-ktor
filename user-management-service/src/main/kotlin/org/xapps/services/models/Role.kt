package org.xapps.services.models

import kotlinx.serialization.Serializable

@Serializable
data class Role(
    var id: Long? = 0,
    var name: String
) {
    companion object {
        const val ADMINISTRATOR = "Administrator"
        const val GUEST = "Guest"
    }
}