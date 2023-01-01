package org.xapps.services.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureSecurity(environment: ApplicationEnvironment) {
    authentication {
        jwt {
            val jwtAudience = environment.config.property("security.audience").getString()
            realm = environment.config.property("security.realm").getString()
            verifier(
                JWT
                    .require(Algorithm.HMAC256(environment.config.property("security.secret").getString()))
                    .withAudience(jwtAudience)
                    .withIssuer(environment.config.property("security.issuer").getString())
                    .build()
            )
            validate { credential ->
                if (credential.payload.subject != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }

}
