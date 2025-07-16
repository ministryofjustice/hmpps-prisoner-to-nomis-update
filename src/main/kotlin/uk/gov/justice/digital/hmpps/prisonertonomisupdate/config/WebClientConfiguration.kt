package uk.gov.justice.digital.hmpps.prisonertonomisupdate.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveAuthorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveHealthWebClient
import java.time.Duration

@Configuration
class WebClientConfiguration(
  @Value("\${api.base.url.nomis}") val nomisApiBaseUri: String,
  @Value("\${api.base.url.mapping}") val mappingBaseUri: String,
  @Value("\${api.base.url.hmpps-auth}") val oauthApiBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:60s}") val timeout: Duration,
  @Value("\${api.mapping-timeout:10s}") val mappingTimeout: Duration,
) {

  @Bean
  fun nomisApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(nomisApiBaseUri, healthTimeout)

  @Bean
  fun nomisApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.reactiveAuthorisedWebClient(
    authorizedClientManager = authorizedClientManager,
    registrationId = "nomis-api",
    url = nomisApiBaseUri,
    timeout = timeout,
  )

  @Bean
  fun mappingHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(mappingBaseUri, healthTimeout)

  @Bean
  fun mappingWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.reactiveAuthorisedWebClient(
    authorizedClientManager = authorizedClientManager,
    registrationId = "mapping-api",
    url = mappingBaseUri,
    timeout = mappingTimeout,
  )

  @Bean
  fun hmppsAuthApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(oauthApiBaseUri, healthTimeout)
}
