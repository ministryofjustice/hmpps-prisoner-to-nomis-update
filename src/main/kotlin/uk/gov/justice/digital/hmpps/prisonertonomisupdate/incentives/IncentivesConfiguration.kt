package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

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
class IncentivesConfiguration(@Value("\${api.base.url.incentives}") val baseUrl: String) {

  @Bean
  fun incentivesApiHealthWebClient(): WebClient {
    return WebClient.builder()
      .baseUrl(baseUrl)
      .build()
  }

  @Bean
  fun incentivesApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
    oauth2Client.setDefaultClientRegistrationId("incentives-api")

    return WebClient.builder()
      .baseUrl(baseUrl)
      .apply(oauth2Client.oauth2Configuration())
      .build()
  }

  @Component("incentivesApi")
  class IncentivesApiHealth
  constructor(@Qualifier("incentivesApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
