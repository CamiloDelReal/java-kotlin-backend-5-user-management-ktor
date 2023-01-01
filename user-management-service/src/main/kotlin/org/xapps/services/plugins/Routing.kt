package org.xapps.services.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.java.KoinJavaComponent.inject
import org.xapps.services.exceptions.EmailNotAvailable
import org.xapps.services.exceptions.InvalidCredentialsException
import org.xapps.services.exceptions.NotFoundException
import org.xapps.services.models.Authentication
import org.xapps.services.models.Login
import org.xapps.services.models.User
import org.xapps.services.services.RoleService
import org.xapps.services.services.UserService
import java.time.Instant
import java.util.*

fun Application.configureRouting() {
    val env = environment
    val userService by inject<UserService>(UserService::class.java)
    val roleService by inject<RoleService>(RoleService::class.java)

    routing {

        fun extractUser(principal: JWTPrincipal): User? {
            return try {
                Json.decodeFromString<User>(principal.payload.subject)
            } catch (ex: Exception) {
                ex.printStackTrace()
                null
            }
        }

        fun isAdministrator(principal: JWTPrincipal): Boolean {
            return try {
                val user = extractUser(principal)
                if (user != null) {
                    userService.isAdministrator(user)
                } else {
                    false
                }
            } catch (ex: Exception) {
                false
            }
        }

        route("/login") {
            post {
                try {
                    val login = call.receive<Login>()
                    val user = userService.validateLogin(login)
                    val expiration =
                        Instant.now().toEpochMilli() + env.config.property("security.validity").getString().toLong()
                    val token = JWT.create()
                        .withAudience(env.config.property("security.audience").getString())
                        .withIssuer(env.config.property("security.issuer").getString())
                        .withSubject(Json.encodeToString(user))
                        .withExpiresAt(Date(expiration))
                        .sign(Algorithm.HMAC256(env.config.property("security.secret").getString()))
                    val auth = Authentication(
                        token = token,
                        type = env.config.property("security.token-type").getString(),
                        expiration = expiration
                    )
                    call.respond(HttpStatusCode.OK, auth)
                } catch (ex: InvalidCredentialsException) {
                    call.respond(HttpStatusCode.Unauthorized)
                } catch (ex: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ex.message.toString())
                }
            }
        }
        route("/roles") {
            get {
                try {
                    val roles = roleService.readAll()
                    call.respond(HttpStatusCode.OK, roles)
                } catch (ex: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ex.message.toString())
                }
            }
        }
        route("/users") {
            authenticate(optional = true) {
                get {
                    val principal = call.principal<JWTPrincipal>()
                    if (principal != null && isAdministrator(principal)) {
                        try {
                            val users = userService.readAll()
                            call.respond(HttpStatusCode.OK, users)
                        } catch (ex: Exception) {
                            call.respond(HttpStatusCode.InternalServerError, ex.message.toString())
                        }
                    } else {
                        call.respond(HttpStatusCode.Unauthorized)
                    }
                }
                get("/{id}") {
                    val principal = call.principal<JWTPrincipal>()
                    val id = call.parameters["id"]!!.toLong()
                    if (principal != null && (isAdministrator(principal) || extractUser(principal)?.id == id)) {
                        try {
                            val user = userService.read(id)
                            call.respond(HttpStatusCode.OK, user)
                        } catch (ex: NotFoundException) {
                            call.respond(HttpStatusCode.NotFound, ex.message.toString())
                        } catch (ex: Exception) {
                            call.respond(HttpStatusCode.InternalServerError, ex.message.toString())
                        }
                    } else {
                        call.respond(HttpStatusCode.Unauthorized)
                    }
                }
                post {
                    var user = call.receive<User>()
                    val principal = call.principal<JWTPrincipal>()
                    if ((principal == null && !userService.hasAdministratorRole(user))
                        || (principal != null && (isAdministrator(principal)) || !userService.hasAdministratorRole(user))
                    ) {
                        try {
                            user = userService.create(user)
                            call.respond(HttpStatusCode.Created, user)
                        } catch (ex: EmailNotAvailable) {
                            call.respond(HttpStatusCode.Conflict, ex.message.toString())
                        } catch (ex: Exception) {
                            call.respond(HttpStatusCode.InternalServerError, ex.message.toString())
                        }
                    } else {
                        call.respond(HttpStatusCode.Unauthorized)
                    }
                }
                put("/{id}") {
                    val id = call.parameters["id"]!!.toLong()
                    val user: User = call.receive<User>()
                    val principal = call.principal<JWTPrincipal>()
                    if (principal != null
                        && (isAdministrator(principal)
                                || (extractUser(principal)?.id == id && !userService.hasAdministratorRole(user)))
                    ) {
                        try {
                            val editedUser = userService.update(id, user)
                            call.respond(HttpStatusCode.OK, editedUser)
                        } catch (ex: NotFoundException) {
                            call.respond(HttpStatusCode.NotFound, ex.message.toString())
                        } catch (ex: EmailNotAvailable) {
                            call.respond(HttpStatusCode.Conflict, ex.message.toString())
                        } catch (ex: Exception) {
                            call.respond(HttpStatusCode.InternalServerError, ex.message.toString())
                        }
                    } else {
                        call.respond(HttpStatusCode.Unauthorized)
                    }
                }
                delete("/{id}") {
                    val id = call.parameters["id"]!!.toLong()
                    val principal = call.principal<JWTPrincipal>()
                    if (principal != null && (isAdministrator(principal) || extractUser(principal)?.id == id)) {
                        try {
                            val success = userService.delete(id)
                            if (success) {
                                call.respond(HttpStatusCode.OK)
                            } else {
                                call.respond(HttpStatusCode.NotFound)
                            }
                        } catch (ex: Exception) {
                            call.respond(HttpStatusCode.InternalServerError, ex.message.toString())
                        }
                    } else {
                        call.respond(HttpStatusCode.Unauthorized)
                    }
                }
            }
        }
    }
}
