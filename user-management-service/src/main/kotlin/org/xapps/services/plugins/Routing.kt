package org.xapps.services.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.xapps.services.exceptions.EmailNotAvailable
import org.xapps.services.exceptions.InvalidCredentialsException
import org.xapps.services.exceptions.NotFoundException
import org.xapps.services.models.Authentication
import org.xapps.services.models.Login
import org.xapps.services.models.User
import org.xapps.services.services.UserService
import org.xapps.services.utils.PropertiesProvider
import java.time.Instant
import java.util.*

fun Application.configureRouting() {

    routing {
        val objectMapper = ObjectMapper()

        fun extractUser(principal: JWTPrincipal): User? {
            return try {
                objectMapper.readValue(principal.payload.subject, User::class.java)
            } catch (ex: Exception) {
                null
            }
        }

        fun isAdministrator(principal: JWTPrincipal): Boolean {
            return try {
                val user = extractUser(principal)
                if (user != null) {
                    UserService.instance.isAdministrator(user)
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
                    val user = UserService.instance.validateLogin(login)
                    val expiration = Instant.now().toEpochMilli() + PropertiesProvider.instance.securityValidity
                    val token = JWT.create()
                        .withAudience(PropertiesProvider.instance.securityAudience)
                        .withIssuer(PropertiesProvider.instance.securityIssuer)
                        .withSubject(ObjectMapper().writeValueAsString(user))
                        .withExpiresAt(Date(expiration))
                        .sign(Algorithm.HMAC256(PropertiesProvider.instance.securitySecret))
                    val auth = Authentication(
                        token = token,
                        type = PropertiesProvider.instance.securityTokenType,
                        expiration = expiration
                    )
                    call.respond(auth)
                } catch (ex: InvalidCredentialsException) {
                    call.respond(HttpStatusCode.Unauthorized)
                } catch (ex: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ex.message.toString())
                }
            }
        }
        route("/users") {
            authenticate(optional = true) {
                get {
                    log.info("GET -> requesting all users")
                    val principal = call.principal<JWTPrincipal>()
                    if (principal != null && isAdministrator(principal)) {
                        try {
                            val users = UserService.instance.readAll()
                            call.respond(users)
                        } catch (ex: Exception) {
                            call.respond(HttpStatusCode.InternalServerError, ex.message.toString())
                        }
                    } else {
                        call.respond(HttpStatusCode.Unauthorized)
                    }
                }
                get("/{id}") {
                    log.info("GET -> requesting user with id")
                    val principal = call.principal<JWTPrincipal>()
                    val id = call.parameters["id"]!!.toLong()
                    log.debug("GET -> requesting user with id $id")
                    if (principal != null && (isAdministrator(principal) || extractUser(principal)?.id == id)) {
                        try {
                            val user = UserService.instance.read(id)
                            call.respond(user)
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
                    log.info("POST -> creating user")
                    var user = call.receive<User>()
                    log.debug("POST creating user $user")
                    val principal = call.principal<JWTPrincipal>()
                    if ((principal == null && !UserService.instance.hasAdministratorRole(user))
                        || (principal != null && (isAdministrator(principal)) || !UserService.instance.hasAdministratorRole(user))
                    ) {
                        try {
                            user = UserService.instance.create(user)
                            call.respond(user)
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
                    log.info("PUT -> editing user")
                    val id = call.parameters["id"]!!.toLong()
                    var user: User = call.receive<User>()
                    log.debug("PUT -> editing user with id $id and data $user")
                    val principal = call.principal<JWTPrincipal>()
                    if (principal != null
                        && (isAdministrator(principal)
                                || (extractUser(principal)?.id == id && !UserService.instance.hasAdministratorRole(user)))) {
                        try {
                            val editedUser = UserService.instance.update(id, user)
                            call.respond(editedUser)
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
                    log.info("DELETE -> edleting user")
                    val id = call.parameters["id"]!!.toLong()
                    log.debug("DELETE -> deleting user with id $id")
                    val principal = call.principal<JWTPrincipal>()
                    if (principal != null && (isAdministrator(principal) || extractUser(principal)?.id == id)) {
                        try {
                            val success = UserService.instance.delete(id)
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
