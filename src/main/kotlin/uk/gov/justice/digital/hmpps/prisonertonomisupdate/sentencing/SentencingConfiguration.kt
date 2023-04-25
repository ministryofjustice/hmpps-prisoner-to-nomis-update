package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

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
class SentencingConfiguration(@Value("\${api.base.url.sentence.adjustments}") val sentenceAdjustmentsUrl: String) {

  @Bean
  fun sentenceAdjustmentsApiHealthWebClient(): WebClient = WebClient.builder()
    .baseUrl(sentenceAdjustmentsUrl)
    .build()

  @Bean
  fun sentenceAdjustmentsApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager).also {
      it.setDefaultClientRegistrationId("sentence-adjustments-api")
    }

    return WebClient.builder()
      .baseUrl(sentenceAdjustmentsUrl)
      .filter(oauth2Client)
      .build()
  }

  @Component("sentenceAdjustmentsApi")
  class SentenceAdjustmentsApiHealth
  constructor(@Qualifier("sentenceAdjustmentsApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
