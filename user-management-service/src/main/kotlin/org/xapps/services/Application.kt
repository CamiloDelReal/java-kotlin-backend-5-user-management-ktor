package org.xapps.services

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.xapps.services.services.RoleService
import org.xapps.services.services.UserService
import org.xapps.services.services.UserRoleService
import org.xapps.services.plugins.configureRouting
import org.xapps.services.plugins.configureSecurity
import org.xapps.services.plugins.configureSerialization

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        RoleService.instance.init()
        UserService.instance.init()
        UserRoleService.instance.init()
        RoleService.instance.seed()
        UserService.instance.seed()
        configureSecurity()
        configureRouting()
        configureSerialization()
    }.start(wait = true)
}
