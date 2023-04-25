package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.health.HealthCheck

@Configuration
class AppointmentsConfiguration(@Value("\${api.base.url.appointments}") val baseUrl: String) {

  @Bean
  fun appointmentsApiHealthWebClient(): WebClient = WebClient.builder()
    .baseUrl(baseUrl)
    .build()

  @Bean
  fun appointmentsApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager).also {
      it.setDefaultClientRegistrationId("appointments-api")
    }

    return WebClient.builder()
      .baseUrl(baseUrl)
      .filter(oauth2Client)
      .build()
  }

  @Component("appointmentsApi")
  class AppointmentsApiHealth
  constructor(@Qualifier("appointmentsApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
