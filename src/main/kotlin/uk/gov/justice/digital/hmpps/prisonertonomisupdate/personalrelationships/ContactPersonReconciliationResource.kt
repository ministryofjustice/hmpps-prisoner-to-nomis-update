package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonReconciliationService.Companion.TELEMETRY_PERSON_PREFIX

@RestController
class ContactPersonReconciliationResource(
  private val telemetryClient: TelemetryClient,
  private val reconciliationService: ContactPersonReconciliationService,
) {

  @PreAuthorize("hasRole('PRISONER_TO_NOMIS__UPDATE__RW')")
  @GetMapping("/persons/{personId}/person-contact/reconciliation")
  suspend fun getPersonContactReconciliationForPerson(@PathVariable personId: Long): MismatchPersonContacts? {
    telemetryClient.trackEvent(
      "$TELEMETRY_PERSON_PREFIX-individual-requested",
      mapOf("personId" to ""),
    )

    return reconciliationService.checkPersonContactMatch(personId)
  }
}
private fun List<MismatchPrisonerContacts>.asPrisonerMap(): Map<String, String> = this.associate { it.offenderNo to "dpsCount=${it.dpsContactCount},nomisCount=${it.nomisContactCount}" }
