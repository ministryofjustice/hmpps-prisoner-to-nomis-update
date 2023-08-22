package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

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
class NonAssociationsConfiguration(@Value("\${api.base.url.non-associations}") val baseUrl: String) {

  @Bean
  fun nonAssociationsApiHealthWebClient(): WebClient = WebClient.builder()
    .baseUrl(baseUrl)
    .build()

  @Bean
  fun nonAssociationsApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager).also {
      it.setDefaultClientRegistrationId("nonAssociations-api")
    }

    return WebClient.builder()
      .baseUrl(baseUrl)
      .filter(oauth2Client)
      .build()
  }

  @Component("nonAssociationsApi")
  class NonAssociationsApiHealth
  constructor(@Qualifier("nonAssociationsApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
