package org.xapps.services.plugins

import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.xapps.services.services.RoleService
import org.xapps.services.services.SeederService
import org.xapps.services.services.UserRoleService
import org.xapps.services.services.UserService

fun Application.configureDI(environment: ApplicationEnvironment) {

    val coreModule = module {
        single { environment }
        single {
            val url = environment.config.property("database.url").getString()
            val driver = environment.config.property("database.driver").getString()
            val user = environment.config.property("database.user").getString()
            val password = environment.config.property("database.password").getString()
            Database.connect(url = url, driver = driver, user = user, password = password)
        }
        single { RoleService(Dispatchers.IO, get()) }
        single { UserService(Dispatchers.IO, get(), get(), get()) }
        single { UserRoleService(get()) }
        single { SeederService(get(), get(), get()) }
    }

    install(Koin) {
        slf4jLogger()
        modules(coreModule)
    }

}