package uk.gov.justice.digital.hmpps.prisonertonomisupdate.config

import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.opentelemetry.api.trace.Span
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.text.ParseException

@Configuration
class ClientTrackingWebFilter : WebFilter {

  override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
    val token = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
    if (token?.startsWith("Bearer ") == true) {
      try {
        val currentSpan = Span.current()
        val jwtBody = getClaimsFromJWT(token)
        val user = jwtBody.getClaim("user_name")?.toString()
        user?.run {
          currentSpan.setAttribute("username", this) // username in customDimensions
          currentSpan.setAttribute("enduser.id", this) // user_Id at the top level of the request
        }
        currentSpan.setAttribute("clientId", jwtBody.getClaim("client_id").toString())
      } catch (e: ParseException) {
        log.warn("problem decoding jwt public key for application insights", e)
      }
    }
    return chain.filter(exchange)
  }

  @Throws(ParseException::class)
  private fun getClaimsFromJWT(token: String): JWTClaimsSet =
    SignedJWT.parse(token.replace("Bearer ", "")).jwtClaimsSet

  companion object {
    private val log = LoggerFactory.getLogger(ClientTrackingWebFilter::class.java)
  }
}
