package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.health.HealthCheck

@Configuration
class VisitsConfiguration(@Value("\${api.base.url.visits}") val baseUrl: String) {

  @Bean
  fun visitsApiHealthWebClient(): WebClient {
    return WebClient.builder()
      .baseUrl(baseUrl)
      .build()
  }

  @Bean
  fun visitsApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
    oauth2Client.setDefaultClientRegistrationId("visits-api")

    return WebClient.builder()
      .baseUrl(baseUrl)
      .apply(oauth2Client.oauth2Configuration())
      .build()
  }

  @Component("visitsApi")
  class VisitsApiHealth
  constructor(@Qualifier("visitsApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
