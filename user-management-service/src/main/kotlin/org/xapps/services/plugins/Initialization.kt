package org.xapps.services.plugins

import io.ktor.server.application.*
import org.koin.java.KoinJavaComponent.inject
import org.xapps.services.services.SeederService

fun Application.initialization() {
    val seederService by inject<SeederService>(SeederService::class.java)
    seederService.seed()
}