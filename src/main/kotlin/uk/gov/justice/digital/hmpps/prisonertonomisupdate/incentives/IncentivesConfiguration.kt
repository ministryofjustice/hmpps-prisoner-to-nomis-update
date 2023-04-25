package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

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
class IncentivesConfiguration(@Value("\${api.base.url.incentives}") val baseUrl: String) {

  @Bean
  fun incentivesApiHealthWebClient(): WebClient = WebClient.builder()
    .baseUrl(baseUrl)
    .build()

  @Bean
  fun incentivesApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager).also {
      it.setDefaultClientRegistrationId("incentives-api")
    }

    return WebClient.builder()
      .baseUrl(baseUrl)
      .filter(oauth2Client)
      .build()
  }

  @Component("incentivesApi")
  class IncentivesApiHealth
  constructor(@Qualifier("incentivesApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
