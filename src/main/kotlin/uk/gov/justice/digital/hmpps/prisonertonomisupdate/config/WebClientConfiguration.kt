package uk.gov.justice.digital.hmpps.prisonertonomisupdate.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfiguration(
  @Value("\${api.base.url.nomis}") val nomisApiBaseUri: String,
  @Value("\${api.base.url.mapping}") val mappingBaseUri: String,
  @Value("\${api.base.url.oauth}") val oauthApiBaseUri: String,
) {

  @Bean
  fun nomisApiHealthWebClient(): WebClient = WebClient.builder().baseUrl(nomisApiBaseUri).build()

  @Bean
  fun mappingHealthWebClient(): WebClient = WebClient.builder().baseUrl(mappingBaseUri).build()

  @Bean
  fun oauthApiHealthWebClient(): WebClient = WebClient.builder().baseUrl(oauthApiBaseUri).build()

  @Bean
  fun nomisApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager).also {
      it.setDefaultClientRegistrationId("nomis-api")
    }

    return WebClient.builder()
      .baseUrl(nomisApiBaseUri)
      .filter(oauth2Client)
      .build()
  }

  @Bean
  fun mappingWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager).also {
      it.setDefaultClientRegistrationId("mapping-api")
    }

    return WebClient.builder()
      .baseUrl(mappingBaseUri)
      .filter(oauth2Client)
      .build()
  }

  @Bean
  fun authorizedClientManager(
    clientRegistrationRepository: ReactiveClientRegistrationRepository,
    oAuth2AuthorizedClientService: ReactiveOAuth2AuthorizedClientService,
  ): ReactiveOAuth2AuthorizedClientManager {
    val authorizedClientProvider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build()
    val authorizedClientManager =
      AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(clientRegistrationRepository, oAuth2AuthorizedClientService)
    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider)
    return authorizedClientManager
  }
}
