package org.xapps.services

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.xapps.services.plugins.*

fun main(args: Array<String>): Unit = EngineMain.main(args)


fun Application.module() {
    configureDI(environment)
    configureSecurity(environment)
    configureRouting()
    configureSerialization()
    initialization()
}