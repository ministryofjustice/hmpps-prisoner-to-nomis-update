package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps

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
class TapConfiguration(
  @Value("\${api.base.url.movements-taps}") val tapsUrl: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.movements-taps-timeout:10s}") val timeout: Duration,
) {

  @Bean
  fun tapsApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(tapsUrl, healthTimeout)

  @Bean
  fun tapsApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.reactiveAuthorisedWebClient(
    authorizedClientManager = authorizedClientManager,
    registrationId = "movements-taps-api",
    url = tapsUrl,
    timeout = timeout,
  )

  @Component("tapsApi")
  class TapsApiHealth(@Qualifier("tapsApiHealthWebClient") webClient: WebClient) : ReactiveHealthPingCheck(webClient)
}
