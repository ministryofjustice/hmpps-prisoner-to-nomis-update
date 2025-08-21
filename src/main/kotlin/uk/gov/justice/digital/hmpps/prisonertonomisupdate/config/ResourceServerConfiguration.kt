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
        "/adjudications/reports/reconciliation",
        "/alerts/reports/reconciliation",
        "/appointments/reports/reconciliation",
        "/casenotes/reports/reconciliation",
        "/contact-person/prisoner-contact/reports/reconciliation",
        "/contact-person/person-contact/reports/reconciliation",
        "/court-sentencing/court-cases/prisoner/reports/reconciliation",
        "/csip/reports/reconciliation",
        "/incentives/reports/reconciliation",
        "/incidents/reports/reconciliation",
        "/locations/reports/reconciliation",
        "/non-associations/reports/reconciliation",
        "/organisations/reports/reconciliation",
        "/sentencing/reports/reconciliation",
        "/visit-balance/reports/reconciliation",
      )
    }
  }
}
