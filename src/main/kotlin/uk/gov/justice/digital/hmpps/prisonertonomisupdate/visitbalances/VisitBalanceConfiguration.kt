package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

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
class VisitBalanceConfiguration(
  @Value("\${api.base.url.visit.balance}") val apiBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:90s}") val timeout: Duration,
) {

  @Bean
  fun visitBalanceApiWebClient(
    authorizedClientManager: ReactiveOAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.reactiveAuthorisedWebClient(authorizedClientManager, registrationId = "visit-balance-api", url = apiBaseUri, timeout)

  @Bean
  fun visitBalanceApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(apiBaseUri, healthTimeout)

  @Component("visitBalanceApi")
  class VisitBalanceApiHealth(@Qualifier("visitBalanceApiHealthWebClient") webClient: WebClient) : ReactiveHealthPingCheck(webClient)
}
