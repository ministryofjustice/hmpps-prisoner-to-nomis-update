package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveAuthorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveHealthWebClient
import uk.gov.justice.hmpps.kotlin.health.ReactiveHealthPingCheck
import java.time.Duration

@Configuration
class CaseNotesConfiguration(
  @Value("\${api.base.url.casenotes}") val caseNotesUrl: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:90s}") val timeout: Duration,
) {

  @Bean
  fun caseNotesApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(caseNotesUrl, healthTimeout)

  @Bean
  fun caseNotesApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient =
    builder.reactiveAuthorisedWebClient(
      authorizedClientManager = authorizedClientManager,
      registrationId = "casenotes-api",
      url = caseNotesUrl,
      timeout = timeout,
    )

  @Component("caseNotesApi")
  class SentenceAdjustmentsApiHealth(@Qualifier("caseNotesApiHealthWebClient") webClient: WebClient) : ReactiveHealthPingCheck(webClient)
}
