package uk.gov.justice.digital.hmpps.prisonertonomisupdate.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.invoke
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.AuthenticationWebFilter
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import uk.gov.justice.hmpps.kotlin.auth.AuthAwareReactiveTokenConverter
import uk.gov.justice.hmpps.kotlin.auth.HmppsReactiveResourceServerConfiguration
import uk.gov.justice.hmpps.kotlin.auth.dsl.ResourceServerConfigurationCustomizer

@Configuration
class ResourceServerConfiguration {
  @Order(Ordered.HIGHEST_PRECEDENCE)
  @Bean
  fun securityWebFilterChain(
    http: ServerHttpSecurity,
    cronjobAuthFilter: AuthenticationWebFilter,
  ): SecurityWebFilterChain = http {
    sessionManagement { SessionCreationPolicy.STATELESS }
    csrf { disable() }
    securityMatcher(PathPatternParserServerWebExchangeMatcher("/queue-admin/purge-queue/**"))
    authorizeExchange {
      authorize(anyExchange, authenticated)
    }
    oauth2ResourceServer { jwt { jwtAuthenticationConverter = AuthAwareReactiveTokenConverter() } }

    addFilterAt(cronjobAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION)
  }

  @Bean
  fun cronjobAuthManager(): ReactiveAuthenticationManager = CronjobAuthenticationManager()

  @Bean
  fun cronjobAuthFilter(
    cronjobAuthenticationManager: ReactiveAuthenticationManager,
    @Value("\${cronjob.auth.secret}") cronjobAuthSecret: String,
  ): AuthenticationWebFilter = AuthenticationWebFilter(cronjobAuthenticationManager).apply {
    setServerAuthenticationConverter(CronjobAuthenticationConverter("X-Auth-CronJob", cronjobAuthSecret))
  }

  @Bean
  fun securityFilterChain(
    http: ServerHttpSecurity,
    resourceServerCustomizer: ResourceServerConfigurationCustomizer,
  ): SecurityWebFilterChain = HmppsReactiveResourceServerConfiguration().hmppsSecurityWebFilterChain(http, resourceServerCustomizer)

  @Bean
  fun resourceServerCustomizer() = ResourceServerConfigurationCustomizer {
    unauthorizedRequestPaths {
      addPaths = setOf(
        "/queue-admin/retry-all-dlqs",
      )
    }
  }
}

class CronjobAuthenticationConverter(
  private val headerName: String,
  private val expectedSecret: String,
) : ServerAuthenticationConverter {
  override fun convert(exchange: ServerWebExchange): Mono<Authentication> = Mono.justOrEmpty(exchange.request.headers.getFirst(headerName))
    .map { cronjobAuthSecret ->
      if (cronjobAuthSecret == expectedSecret) {
        UsernamePasswordAuthenticationToken.authenticated(
          "cronJob",
          cronjobAuthSecret,
          mutableListOf(SimpleGrantedAuthority("ROLE_QUEUE_ADMIN")),
        )
      } else {
        UsernamePasswordAuthenticationToken.unauthenticated("cronJob", cronjobAuthSecret)
      }
    }
}

class CronjobAuthenticationManager : ReactiveAuthenticationManager {
  override fun authenticate(authentication: Authentication): Mono<Authentication> = Mono.just(authentication)
}
