package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

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
class CourtSentencingConfiguration(
  @Value("\${api.base.url.court.sentencing}") val courtSentencingUrl: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.court-sentencing-timeout:10s}") val timeout: Duration,
) {

  @Bean
  fun courtSentencingApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(courtSentencingUrl, healthTimeout)

  @Bean
  fun courtSentencingApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.reactiveAuthorisedWebClient(
    authorizedClientManager = authorizedClientManager,
    registrationId = "court-sentencing-api",
    url = courtSentencingUrl,
    timeout = timeout,
  )

  @Component("courtSentencingApi")
  class CourtSentencingApiHealth(@Qualifier("courtSentencingApiHealthWebClient") webClient: WebClient) : ReactiveHealthPingCheck(webClient)
}
