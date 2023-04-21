package uk.gov.justice.digital.hmpps.prisonertonomisupdate.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CoroutineScopes {
  @Bean
  fun reportScope() = CoroutineScope(Dispatchers.Unconfined)
}
