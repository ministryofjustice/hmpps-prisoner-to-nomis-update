package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

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
class StaffConfiguration(
  @Value("\${api.base.url.staff}") val apiBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:60s}") val timeout: Duration,
) {

  @Bean
  fun staffApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(apiBaseUri, healthTimeout)

  @Bean
  fun staffApiWebClient(
    authorizedClientManager: ReactiveOAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.reactiveAuthorisedWebClient(authorizedClientManager, registrationId = "staff-api", url = apiBaseUri, timeout)

  @Component("staff")
  class PersonRelationshipsApiHealth(@Qualifier("staffApiHealthWebClient") webClient: WebClient) : ReactiveHealthPingCheck(webClient)
}
