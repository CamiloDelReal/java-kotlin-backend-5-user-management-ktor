package org.xapps.services.plugins

import io.ktor.server.auth.*
import io.ktor.util.*
import io.ktor.server.auth.jwt.*
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import org.xapps.services.models.User
import org.xapps.services.utils.PropertiesProvider

fun Application.configureSecurity() {

    authentication {
            jwt {
                val jwtAudience = PropertiesProvider.instance.securityAudience
                realm = PropertiesProvider.instance.secudityRealm
                verifier(
                    JWT
                        .require(Algorithm.HMAC256(PropertiesProvider.instance.securitySecret))
                        .withAudience(jwtAudience)
                        .withIssuer(PropertiesProvider.instance.securityIssuer)
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
