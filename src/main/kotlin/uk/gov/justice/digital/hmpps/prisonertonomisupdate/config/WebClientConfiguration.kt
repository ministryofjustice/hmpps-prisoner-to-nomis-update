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

@Configuration
class WebClientConfiguration(
  @Value("\${api.base.url.nomis}") val nomisApiBaseUri: String,
  @Value("\${api.base.url.mapping}") val mappingBaseUri: String,
  @Value("\${api.base.url.oauth}") val oauthApiBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:90s}") val timeout: Duration,
) {

  @Bean
  fun nomisApiHealthWebClient(builder: WebClient.Builder): WebClient = healthWebClient(builder, nomisApiBaseUri)

  @Bean
  fun nomisApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient =
    authorisedWebClient(authorizedClientManager, builder, registrationId = "nomis-api", url = nomisApiBaseUri)

  @Bean
  fun mappingHealthWebClient(builder: WebClient.Builder): WebClient = healthWebClient(builder, mappingBaseUri)

  @Bean
  fun mappingWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient =
    authorisedWebClient(authorizedClientManager, builder, registrationId = "mapping-api", url = mappingBaseUri)

  @Bean
  fun oauthApiHealthWebClient(builder: WebClient.Builder): WebClient = healthWebClient(builder, oauthApiBaseUri)

  private fun authorisedWebClient(
    authorizedClientManager: ReactiveOAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
    registrationId: String,
    url: String,
  ): WebClient {
    val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager).also {
      it.setDefaultClientRegistrationId(registrationId)
    }

    return builder.baseUrl(url)
      .clientConnector(ReactorClientHttpConnector(HttpClient.create().responseTimeout(timeout)))
      .filter(oauth2Client)
      .build()
  }

  @Bean
  fun authorizedClientManager(
    clientRegistrationRepository: ReactiveClientRegistrationRepository,
    oAuth2AuthorizedClientService: ReactiveOAuth2AuthorizedClientService,
  ): ReactiveOAuth2AuthorizedClientManager {
    val authorizedClientProvider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build()
    return AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
      clientRegistrationRepository,
      oAuth2AuthorizedClientService,
    ).also { it.setAuthorizedClientProvider(authorizedClientProvider) }
  }

  private fun healthWebClient(builder: WebClient.Builder, url: String): WebClient = builder
    .baseUrl(url)
    .clientConnector(ReactorClientHttpConnector(HttpClient.create().responseTimeout(healthTimeout)))
    .build()
}
