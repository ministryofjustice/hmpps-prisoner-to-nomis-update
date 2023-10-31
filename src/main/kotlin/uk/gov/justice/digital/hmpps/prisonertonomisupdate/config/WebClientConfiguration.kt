package uk.gov.justice.digital.hmpps.prisonertonomisupdate.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import kotlin.apply as kotlinApply

@Configuration
class WebClientConfiguration(
  @Value("\${api.base.url.nomis}") val nomisApiBaseUri: String,
  @Value("\${api.base.url.mapping}") val mappingBaseUri: String,
  @Value("\${api.base.url.oauth}") val oauthApiBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:90s}") val timeout: Duration,
) {

  @Bean
  fun nomisApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(nomisApiBaseUri, healthTimeout)

  @Bean
  fun nomisApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient =
    builder.authorisedWebClient(authorizedClientManager, registrationId = "nomis-api", url = nomisApiBaseUri, timeout)

  @Bean
  fun mappingHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(mappingBaseUri, healthTimeout)

  @Bean
  fun mappingWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient =
    builder.authorisedWebClient(authorizedClientManager, registrationId = "mapping-api", url = mappingBaseUri, timeout)

  @Bean
  fun oauthApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(oauthApiBaseUri, healthTimeout)

  @Bean
  fun authorizedClientManager(
    clientRegistrationRepository: ReactiveClientRegistrationRepository,
    oAuth2AuthorizedClientService: ReactiveOAuth2AuthorizedClientService,
  ): ReactiveOAuth2AuthorizedClientManager {
    val authorizedClientProvider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build()
    return AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
      clientRegistrationRepository,
      oAuth2AuthorizedClientService,
    ).kotlinApply { setAuthorizedClientProvider(authorizedClientProvider) }
  }
}

fun WebClient.Builder.authorisedWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, registrationId: String, url: String, timeout: Duration): WebClient {
  val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager).kotlinApply {
    setDefaultClientRegistrationId(registrationId)
  }

  return baseUrl(url)
    .clientConnector(ReactorClientHttpConnector(HttpClient.create().responseTimeout(timeout)))
    .filter(oauth2Client)
    .build()
}

fun WebClient.Builder.healthWebClient(url: String, healthTimeout: Duration): WebClient =
  baseUrl(url)
    .clientConnector(ReactorClientHttpConnector(HttpClient.create().responseTimeout(healthTimeout)))
    .build()
