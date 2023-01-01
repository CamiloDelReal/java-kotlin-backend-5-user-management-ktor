package org.xapps.services

import io.ktor.client.call.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import org.junit.After
import org.junit.Test
import org.koin.core.context.GlobalContext.stopKoin
import org.xapps.services.models.Authentication
import org.xapps.services.models.Role
import org.xapps.services.models.User
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ApplicationTest {

    @After
    fun teardown() {
        stopKoin()
    }

    @Test
    fun loginRoot_success() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val authentication: Authentication = client.post {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                {
                    "email": "root@gmail.com",
                    "password": "123456"
                }
            """.trimIndent()
            )
            url {
                url("/login")
            }
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }.body()

        assertNotNull(authentication.token)
        assertNotNull(authentication.type)
        assertNotNull(authentication.expiration)
    }

    @Test
    fun loginRoot_failByInvalidPassword() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        client.post {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                {
                    "email" : "root@gmail.com",
                    "password" : "invalid"
                }
            """.trimIndent()
            )
            url {
                url("/login")
            }
        }.apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun login_failByInvalidCredentials() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        client.post {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                {
                    "email" : "invalid",
                    "password" : "123456"
                }
            """.trimIndent()
            )
            url {
                url("/login")
            }
        }.apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun createAndEditUserWithDefaultRole_success() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val createdUser: User = client.post {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                  {
                    "email" : "vladdoe@gmail.com",
                    "password" : "qwerty",
                    "firstName": "Vlad",
                    "lastName": "Doe"
                  }
              """.trimIndent()
            )
            url {
                url("/users")
            }
        }.apply {
            assertEquals(HttpStatusCode.Created, status)
        }.body()

        assertNotNull(createdUser.id)
        assertNotEquals(0, createdUser.id)
        assertEquals("vladdoe@gmail.com", createdUser.email)
        assertEquals("Vlad", createdUser.firstName)
        assertEquals("Doe", createdUser.lastName)
        assertNotNull(createdUser.roles)
        assertEquals(1, createdUser.roles!!.size)
        assertEquals(Role.GUEST, createdUser.roles!![0].name)

        val authentication: Authentication = client.post {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                {
                    "email": "vladdoe@gmail.com",
                    "password": "qwerty"
                }
            """.trimIndent()
            )
            url {
                url("/login")
            }
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }.body()

        val clientWithAuth = createClient {
            install(ContentNegotiation) {
                json()
            }
            install(Auth) {
                bearer {
                    loadTokens {
                        BearerTokens(authentication.token, "there is no refresh token")
                    }
                }
            }
        }

        val updatedUser: User = clientWithAuth.put {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                  {
                    "email" : "annadoe@gmail.com",
                    "password" : "qwerty",
                    "firstName": "Anna",
                    "lastName": "Doe"
                  }
              """.trimIndent()
            )
            url {
                url("/users/${createdUser.id}")
            }
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }.body()

        assertNotNull(updatedUser.id)
        assertEquals(createdUser.id, updatedUser.id)
        assertEquals("annadoe@gmail.com", updatedUser.email)
        assertEquals("Anna", updatedUser.firstName)
        assertEquals("Doe", updatedUser.lastName)
        assertNotNull(createdUser.roles)
        assertEquals(1, createdUser.roles!!.size)
        assertEquals(Role.GUEST, createdUser.roles!![0].name)
    }

    @Test
    fun createUserWithAdminRole_failByNoAdminCredentials() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val roles: List<Role> = client.get {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            url {
                url("/roles")
            }
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }.body()

        assertEquals(2, roles.size)

        val adminRole = roles.find { it.name == Role.ADMINISTRATOR }
        assertNotNull(adminRole)

        client.post {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody("""
                  {
                    "email" : "johndoe@gmail.com",
                    "password" : "qwerty",
                    "firstName": "John",
                    "lastName": "Doe",
                    "roles": [
                      {
                        "id": ${adminRole.id},
                        "name": "${adminRole.name}"
                      }
                    ]
                  }
                  """.trimIndent())
            url {
                url("/users")
            }
        }.apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun createUserWithAdminRole_success() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val rootAuthentication: Authentication = client.post {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                {
                    "email": "root@gmail.com",
                    "password": "123456"
                }
            """.trimIndent()
            )
            url {
                url("/login")
            }
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }.body()

        assertNotNull(rootAuthentication.token)
        assertNotNull(rootAuthentication.type)
        assertNotNull(rootAuthentication.expiration)

        val roles: List<Role> = client.get {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            url {
                url("/roles")
            }
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }.body()

        assertEquals(2, roles.size)

        val adminRole = roles.find { it.name == Role.ADMINISTRATOR }
        assertNotNull(adminRole)

        val clientWithAuth = createClient {
            install(ContentNegotiation) {
                json()
            }
            install(Auth) {
                bearer {
                    loadTokens {
                        BearerTokens(rootAuthentication.token, "there is no refresh token")
                    }
                }
            }
        }

        val createdAdmin: User = clientWithAuth.post {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody("""
                  {
                    "email" : "kathdoe@gmail.com",
                    "password" : "qwerty",
                    "firstName": "Kath",
                    "lastName": "Doe",
                    "roles": [
                      {
                        "id": ${adminRole.id},
                        "name": "${adminRole.name}"
                      }
                    ]
                  }
                  """.trimIndent())
            url {
                url("/users")
            }
        }.apply {
            assertEquals(HttpStatusCode.Created, status)
        }.body()

        assertNotNull(createdAdmin.id)
        assertNotEquals(0, createdAdmin.id)
        assertEquals("kathdoe@gmail.com", createdAdmin.email)
        assertNull(createdAdmin.password)
        assertNotNull(createdAdmin.roles)
        assertEquals(1, createdAdmin.roles!!.size)
        assertEquals(Role.ADMINISTRATOR, createdAdmin.roles!![0].name)
    }

    @Test
    fun createUser_failByUsernameDuplicity() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        client.post {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody("""
                  {
                    "email" : "root@gmail.com",
                    "password" : "qwerty",
                    "firstName": "Root",
                    "lastName": "Second"
                  }
                  """.trimIndent())
            url {
                url("/users")
            }
        }.apply {
            assertEquals(HttpStatusCode.Conflict, status)
        }
    }

    @Test
    fun createAndEditUserWithUserCredentials_failByWrongId() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val createdUser: User = client.post {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                  {
                    "email" : "robdoe@gmail.com",
                    "password" : "qwerty",
                    "firstName": "Rob",
                    "lastName": "Doe"
                  }
              """.trimIndent()
            )
            url {
                url("/users")
            }
        }.apply {
            assertEquals(HttpStatusCode.Created, status)
        }.body()

        assertNotNull(createdUser.id)
        assertNotEquals(0, createdUser.id)
        assertEquals("robdoe@gmail.com", createdUser.email)
        assertEquals("Rob", createdUser.firstName)
        assertEquals("Doe", createdUser.lastName)
        assertNotNull(createdUser.roles)
        assertEquals(1, createdUser.roles!!.size)
        assertEquals(Role.GUEST, createdUser.roles!![0].name)

        val authentication: Authentication = client.post {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                {
                    "email": "robdoe@gmail.com",
                    "password": "qwerty"
                }
            """.trimIndent()
            )
            url {
                url("/login")
            }
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }.body()

        val clientWithAuth = createClient {
            install(ContentNegotiation) {
                json()
            }
            install(Auth) {
                bearer {
                    loadTokens {
                        BearerTokens(authentication.token, "there is no refresh token")
                    }
                }
            }
        }

        clientWithAuth.put {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                  {
                    "email" : "robdoe@gmail.com",
                    "password" : "qwerty",
                    "firstName": "Robert",
                    "lastName": "Doe"
                  }
              """.trimIndent()
            )
            url {
                url("/users/${createdUser.id + 999}")
            }
        }.apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun editUserToAdminWithUserCredentials_failByNoAdminCredentials() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val createdUser: User = client.post {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                  {
                    "email" : "donalddoe@gmail.com",
                    "password" : "qwerty",
                    "firstName": "Donald",
                    "lastName": "Doe"
                  }
              """.trimIndent()
            )
            url {
                url("/users")
            }
        }.apply {
            assertEquals(HttpStatusCode.Created, status)
        }.body()

        assertNotNull(createdUser.id)
        assertNotEquals(0, createdUser.id)
        assertEquals("donalddoe@gmail.com", createdUser.email)
        assertEquals("Donald", createdUser.firstName)
        assertEquals("Doe", createdUser.lastName)
        assertNotNull(createdUser.roles)
        assertEquals(1, createdUser.roles!!.size)
        assertEquals(Role.GUEST, createdUser.roles!![0].name)

        val authentication: Authentication = client.post {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                {
                    "email": "donalddoe@gmail.com",
                    "password": "qwerty"
                }
            """.trimIndent()
            )
            url {
                url("/login")
            }
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }.body()

        val roles: List<Role> = client.get {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            url {
                url("/roles")
            }
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }.body()

        assertEquals(2, roles.size)

        val adminRole = roles.find { it.name == Role.ADMINISTRATOR }
        assertNotNull(adminRole)

        val clientWithAuth = createClient {
            install(ContentNegotiation) {
                json()
            }
            install(Auth) {
                bearer {
                    loadTokens {
                        BearerTokens(authentication.token, "there is no refresh token")
                    }
                }
            }
        }

        clientWithAuth.put {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                  {
                    "email" : "donalddoe@gmail.com",
                    "password" : "qwerty",
                    "firstName": "Donald",
                    "lastName": "Doe",
                    "roles": [
                      {
                        "id": ${adminRole.id},
                        "name": "${adminRole.name}"
                      }
                    ]
                  }
              """.trimIndent()
            )
            url {
                url("/users/${createdUser.id}")
            }
        }.apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun editUser_failByNoUserCredentials() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val createdUser: User = client.post {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                  {
                    "email" : "lindadoe@gmail.com",
                    "password" : "qwerty",
                    "firstName": "Linda",
                    "lastName": "Doe"
                  }
              """.trimIndent()
            )
            url {
                url("/users")
            }
        }.apply {
            assertEquals(HttpStatusCode.Created, status)
        }.body()

        assertNotNull(createdUser.id)
        assertNotEquals(0, createdUser.id)
        assertEquals("lindadoe@gmail.com", createdUser.email)
        assertEquals("Linda", createdUser.firstName)
        assertEquals("Doe", createdUser.lastName)
        assertNotNull(createdUser.roles)
        assertEquals(1, createdUser.roles!!.size)
        assertEquals(Role.GUEST, createdUser.roles!![0].name)

        client.put {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                  {
                    "email" : "robdoe@gmail.com",
                    "password" : "qwerty",
                    "firstName": "Linda",
                    "lastName": "McDoe"
                  }
              """.trimIndent()
            )
            url {
                url("/users/${createdUser.id}")
            }
        }.apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun editUserWithAdminCredentials_success() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val createdUser: User = client.post {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                  {
                    "email" : "joanadoe@gmail.com",
                    "password" : "qwerty",
                    "firstName": "Joana",
                    "lastName": "Doe"
                  }
              """.trimIndent()
            )
            url {
                url("/users")
            }
        }.apply {
            assertEquals(HttpStatusCode.Created, status)
        }.body()

        assertNotNull(createdUser.id)
        assertNotEquals(0, createdUser.id)
        assertEquals("joanadoe@gmail.com", createdUser.email)
        assertEquals("Joana", createdUser.firstName)
        assertEquals("Doe", createdUser.lastName)
        assertNotNull(createdUser.roles)
        assertEquals(1, createdUser.roles!!.size)
        assertEquals(Role.GUEST, createdUser.roles!![0].name)

        val authentication: Authentication = client.post {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                {
                    "email": "root@gmail.com",
                    "password": "123456"
                }
            """.trimIndent()
            )
            url {
                url("/login")
            }
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }.body()

        val clientWithAuth = createClient {
            install(ContentNegotiation) {
                json()
            }
            install(Auth) {
                bearer {
                    loadTokens {
                        BearerTokens(authentication.token, "there is no refresh token")
                    }
                }
            }
        }

        val updatedUser: User = clientWithAuth.put {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                  {
                    "email" : "joanadoe@gmail.com",
                    "password" : "qwerty",
                    "firstName": "Joana",
                    "lastName": "McDoe"
                  }
              """.trimIndent()
            )
            url {
                url("/users/${createdUser.id}")
            }
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }.body()

        assertNotNull(updatedUser.id)
        assertEquals(createdUser.id, updatedUser.id)
        assertEquals("joanadoe@gmail.com", updatedUser.email)
        assertEquals("Joana", updatedUser.firstName)
        assertEquals("McDoe", updatedUser.lastName)
        assertNotNull(createdUser.roles)
        assertEquals(1, createdUser.roles!!.size)
        assertEquals(Role.GUEST, createdUser.roles!![0].name)
    }

    @Test
    fun deleteUserWithUserCredentials_success() = testApplication() {
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val createdUser: User = client.post {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                  {
                    "email" : "ninadoe@gmail.com",
                    "password" : "qwerty",
                    "firstName": "Nina",
                    "lastName": "Doe"
                  }
              """.trimIndent()
            )
            url {
                url("/users")
            }
        }.apply {
            assertEquals(HttpStatusCode.Created, status)
        }.body()

        assertNotNull(createdUser.id)
        assertNotEquals(0, createdUser.id)
        assertEquals("ninadoe@gmail.com", createdUser.email)
        assertEquals("Nina", createdUser.firstName)
        assertEquals("Doe", createdUser.lastName)
        assertNotNull(createdUser.roles)
        assertEquals(1, createdUser.roles!!.size)
        assertEquals(Role.GUEST, createdUser.roles!![0].name)

        val authentication: Authentication = client.post {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                {
                    "email": "ninadoe@gmail.com",
                    "password": "qwerty"
                }
            """.trimIndent()
            )
            url {
                url("/login")
            }
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }.body()

        val clientWithAuth = createClient {
            install(ContentNegotiation) {
                json()
            }
            install(Auth) {
                bearer {
                    loadTokens {
                        BearerTokens(authentication.token, "there is no refresh token")
                    }
                }
            }
        }

        clientWithAuth.delete {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            url {
                url("/users/${createdUser.id}")
            }
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun deleteUser_failByNoCredentials() = testApplication() {
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val createdUser: User = client.post {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                  {
                    "email" : "ninadoe@gmail.com",
                    "password" : "qwerty",
                    "firstName": "Nina",
                    "lastName": "Doe"
                  }
              """.trimIndent()
            )
            url {
                url("/users")
            }
        }.apply {
            assertEquals(HttpStatusCode.Created, status)
        }.body()

        assertNotNull(createdUser.id)
        assertNotEquals(0, createdUser.id)
        assertEquals("ninadoe@gmail.com", createdUser.email)
        assertEquals("Nina", createdUser.firstName)
        assertEquals("Doe", createdUser.lastName)
        assertNotNull(createdUser.roles)
        assertEquals(1, createdUser.roles!!.size)
        assertEquals(Role.GUEST, createdUser.roles!![0].name)

        client.delete {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            url {
                url("/users/${createdUser.id}")
            }
        }.apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }
}