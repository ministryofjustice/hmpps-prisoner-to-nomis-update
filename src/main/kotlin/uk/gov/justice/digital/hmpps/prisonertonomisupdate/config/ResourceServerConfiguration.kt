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
        "/incentives/reports/reconciliation",
        "/non-associations/reports/reconciliation",
        "/locations/reports/reconciliation",
        "/allocations/reports/reconciliation",
        "/attendances/reports/reconciliation",
        "/sentencing/reports/reconciliation",
        "/adjudications/reports/reconciliation",
        "/alerts/reports/reconciliation",
        "/prisonperson/reports/reconciliation",
        "/activities/mappings/unknown-mappings",
      )
    }
  }
}
