package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

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
class AdjudicationsConfiguration(@Value("\${api.base.url.adjudications}") val adjudicationsUrl: String) {

  @Bean
  fun adjudicationsApiHealthWebClient(): WebClient = WebClient.builder()
    .baseUrl(adjudicationsUrl)
    .build()

  @Bean
  fun adjudicationsApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager).also {
      it.setDefaultClientRegistrationId("adjudications-api")
    }

    return WebClient.builder()
      .baseUrl(adjudicationsUrl)
      .filter(oauth2Client)
      .build()
  }

  @Component("adjudicationsApi")
  class AdjudicationsApiHealth(@Qualifier("adjudicationsApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
