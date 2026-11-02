package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation

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
class PersonLocationConfiguration(
  @Value("\${api.base.url.person-location}") val personLocationUrl: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.person-location-timeout:10s}") val dpsTimeout: Duration,
) {

  @Bean
  fun personLocationDpsApiWebClient(
    authorizedClientManager: ReactiveOAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.reactiveAuthorisedWebClient(authorizedClientManager, registrationId = "person-location-api", url = personLocationUrl, dpsTimeout)

  @Bean
  fun personLocationApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(personLocationUrl, healthTimeout)

  @Component("personLocationApi")
  class PersonLocationApiHealth(@Qualifier("personLocationApiHealthWebClient") webClient: WebClient) : ReactiveHealthPingCheck(webClient)
}
