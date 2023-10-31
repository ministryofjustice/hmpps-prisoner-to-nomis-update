package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.authorisedWebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.healthWebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.health.HealthCheck
import java.time.Duration

@Configuration
class SentencingConfiguration(
  @Value("\${api.base.url.sentence.adjustments}") val sentenceAdjustmentsUrl: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:90s}") val timeout: Duration,
) {

  @Bean
  fun sentenceAdjustmentsApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(sentenceAdjustmentsUrl, healthTimeout)

  @Bean
  fun sentenceAdjustmentsApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient =
    builder.authorisedWebClient(authorizedClientManager, registrationId = "sentence-adjustments-api", url = sentenceAdjustmentsUrl, timeout)

  @Component("sentenceAdjustmentsApi")
  class SentenceAdjustmentsApiHealth(@Qualifier("sentenceAdjustmentsApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
