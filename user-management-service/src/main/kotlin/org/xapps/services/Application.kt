package org.xapps.services

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.xapps.services.plugins.*
import org.xapps.services.services.RoleService
import org.xapps.services.services.UserService
import org.xapps.services.services.UserRoleService

//fun main() {
//    embeddedServer(
//        Netty
//    ) {
//        RoleService.instance.init()
//        UserService.instance.init()
//        UserRoleService.instance.init()
//        RoleService.instance.seed()
//        UserService.instance.seed()
//        configureSecurity()
//        configureRouting()
//        configureSerialization()
//    }.start(wait = true)
//}


fun main(args: Array<String>): Unit = EngineMain.main(args)


fun Application.module() {
    configureDI(environment)
    configureSecurity(environment)
    configureRouting()
    configureSerialization()
    initialization()
}