package uk.gov.justice.digital.hmpps.prisonertonomisupdate.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.hmpps.kotlin.auth.dsl.ResourceServerConfigurationCustomizer

@Configuration
class ResourceServerConfiguration {
  @Bean
  fun resourceServerCustomizer() = ResourceServerConfigurationCustomizer {
    unauthorizedRequestPaths {
      addPaths = setOf(
        "/queue-admin/retry-all-dlqs",
        "/incidents/reports/reconciliation",
        "/locations/reports/reconciliation",
        "/non-associations/reports/reconciliation",
        "/organisations/reports/reconciliation",
        "/sentencing/reports/reconciliation",
        "/transactions/reports/reconciliation",
        "/visit-balance/reports/reconciliation",
      )
    }
  }
}
