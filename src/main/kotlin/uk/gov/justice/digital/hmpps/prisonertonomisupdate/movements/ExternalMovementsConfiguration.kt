package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveAuthorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveHealthWebClient
import java.time.Duration

@Configuration
class ExternalMovementsConfiguration(
  @Value("\${api.base.url.ext.movements}") val movementsUrl: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.ext-movements-timeout:10s}") val timeout: Duration,
) {

  @Bean
  fun movementsApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(movementsUrl, healthTimeout)

  @Bean
  fun movementsApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.reactiveAuthorisedWebClient(
    authorizedClientManager = authorizedClientManager,
    registrationId = "movements-api",
    url = movementsUrl,
    timeout = timeout,
  )

  // TODO turn on the health check once external movements API is available in prod
//  @Component("movementsApi")
//  class MovementsApiHealth(@Qualifier("movementsApiHealthWebClient") webClient: WebClient) : ReactiveHealthPingCheck(webClient)
}
