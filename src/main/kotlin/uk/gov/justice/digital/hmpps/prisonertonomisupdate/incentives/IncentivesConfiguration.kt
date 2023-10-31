package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.authorisedWebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.healthWebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.health.HealthCheck
import java.time.Duration

@Configuration
class IncentivesConfiguration(
  @Value("\${api.base.url.incentives}") val baseUrl: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:90s}") val timeout: Duration,
) {

  @Bean
  fun incentivesApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(baseUrl, healthTimeout)

  @Bean
  fun incentivesApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient =
    builder.authorisedWebClient(authorizedClientManager, registrationId = "incentives-api", url = baseUrl, timeout)

  @Component("incentivesApi")
  class IncentivesApiHealth(@Qualifier("incentivesApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
