package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

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
class ActivitiesConfiguration(@Value("\${api.base.url.activities}") val baseUrl: String) {

  @Bean
  fun activitiesApiHealthWebClient(): WebClient = WebClient.builder()
    .baseUrl(baseUrl)
    .build()

  @Bean
  fun activitiesApiWebClient(builder: WebClient.Builder, authorizedClientManager: ReactiveOAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager).also {
      it.setDefaultClientRegistrationId("activities-api")
    }

    return builder
      .baseUrl(baseUrl)
      .filter(oauth2Client)
      .build()
  }

  @Component("activitiesApi")
  class ActivitiesApiHealth
  constructor(@Qualifier("activitiesApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
