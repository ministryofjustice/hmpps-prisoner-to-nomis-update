package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveAuthorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveHealthWebClient
import uk.gov.justice.hmpps.kotlin.health.ReactiveHealthPingCheck
import java.time.Duration

@Configuration
class IncidentsConfiguration(
  @Value("\${api.base.url.incidents}") val incidentsUrl: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:90s}") val timeout: Duration,
) {

  @Bean
  fun incidentsApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(incidentsUrl, healthTimeout)

  @Bean
  fun incidentsApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.reactiveAuthorisedWebClient(
    authorizedClientManager = authorizedClientManager,
    registrationId = "incidents-api",
    url = incidentsUrl,
    timeout = timeout,
  )

  @Component("incidentsApi")
  class IncidentsApiHealth(@Qualifier("incidentsApiHealthWebClient") webClient: WebClient) : ReactiveHealthPingCheck(webClient)
}
