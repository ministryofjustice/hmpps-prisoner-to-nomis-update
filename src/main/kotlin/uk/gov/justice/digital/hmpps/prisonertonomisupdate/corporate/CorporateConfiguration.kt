package uk.gov.justice.digital.hmpps.prisonertonomisupdate.corporate

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveAuthorisedWebClient
import java.time.Duration

@Configuration
class CorporateConfiguration(
  @Value("\${api.base.url.organisations}") val apiBaseUri: String,
  @Value("\${api.timeout:90s}") val timeout: Duration,
) {

  @Bean
  fun corporateDpsApiWebClient(
    authorizedClientManager: ReactiveOAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.reactiveAuthorisedWebClient(authorizedClientManager, registrationId = "organisations-api", url = apiBaseUri, timeout)
}
