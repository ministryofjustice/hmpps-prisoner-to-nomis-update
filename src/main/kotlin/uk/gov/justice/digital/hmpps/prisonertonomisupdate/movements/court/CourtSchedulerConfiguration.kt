package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

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
class CourtSchedulerConfiguration(
  @Value("\${api.base.url.movements-court}") val courtSchedulerUrl: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.movements-court-timeout:10s}") val timeout: Duration,
) {

  @Bean
  fun courtSchedulerApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(courtSchedulerUrl, healthTimeout)

  @Bean
  fun courtSchedulerApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.reactiveAuthorisedWebClient(
    authorizedClientManager = authorizedClientManager,
    registrationId = "movements-court-api",
    url = courtSchedulerUrl,
    timeout = timeout,
  )

  @Component("courtSchedulerApi")
  class CourtSchedulerApiHealth(@Qualifier("courtSchedulerApiHealthWebClient") webClient: WebClient) : ReactiveHealthPingCheck(webClient)
}
