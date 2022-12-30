package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

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
class ActivitiesConfiguration(@Value("\${api.base.url.activities}") val baseUrl: String) {

  @Bean
  fun activitiesApiHealthWebClient(): WebClient {
    return WebClient.builder()
      .baseUrl(baseUrl)
      .build()
  }

  @Bean
  fun activitiesApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
    oauth2Client.setDefaultClientRegistrationId("activities-api")

    return WebClient.builder()
      .baseUrl(baseUrl)
      .apply(oauth2Client.oauth2Configuration())
      .build()
  }

  @Component("activitiesApi")
  class ActivitiesApiHealth
  constructor(@Qualifier("activitiesApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
