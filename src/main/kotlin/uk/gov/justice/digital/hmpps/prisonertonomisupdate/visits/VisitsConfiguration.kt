package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

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
class VisitsConfiguration(@Value("\${api.base.url.visits}") val baseUrl: String) {

  @Bean
  fun visitsApiHealthWebClient(): WebClient = WebClient.builder()
    .baseUrl(baseUrl)
    .build()

  @Bean
  fun visitsApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager).also {
      it.setDefaultClientRegistrationId("visits-api")
    }

    return WebClient.builder()
      .baseUrl(baseUrl)
      .filter(oauth2Client)
      .build()
  }

  @Component("visitsApi")
  class VisitsApiHealth
  constructor(@Qualifier("visitsApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
