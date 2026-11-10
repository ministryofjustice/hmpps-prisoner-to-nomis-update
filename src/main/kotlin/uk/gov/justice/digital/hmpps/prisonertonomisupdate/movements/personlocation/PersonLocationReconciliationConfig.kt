package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("reports.person-location.reconciliation")
data class PersonLocationReconciliationConfig(
  val pageSize: Int = 100,
  val threadCount: Int = 15,
  val reconcileBookings: Boolean = true,
)
