package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonReconciliationService.Companion.TELEMETRY_PERSON_PREFIX
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonReconciliationService.Companion.TELEMETRY_PRISONER_PREFIX

@RestController
class ContactPersonReconciliationResource(
  private val telemetryClient: TelemetryClient,
  private val reconciliationService: ContactPersonReconciliationService,
  private val reportScope: CoroutineScope,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/contact-person/prisoner-contact/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun generatePrisonerContactReconciliationReport() {
    telemetryClient.trackEvent(
      "$TELEMETRY_PRISONER_PREFIX-requested",
      mapOf(),
    )

    reportScope.launch {
      runCatching { reconciliationService.generatePrisonerContactReconciliationReport() }
        .onSuccess {
          log.info("Prisoner contacts reconciliation report completed with ${it.mismatches.size} mismatches")
          telemetryClient.trackEvent(
            "$TELEMETRY_PRISONER_PREFIX-report",
            mapOf(
              "prisoners-count" to it.itemsChecked.toString(),
              "pages-count" to it.pagesChecked.toString(),
              "mismatch-count" to it.mismatches.size.toString(),
              "success" to "true",
            ) + it.mismatches.asPrisonerMap(),
          )
        }
        .onFailure {
          telemetryClient.trackEvent("$TELEMETRY_PRISONER_PREFIX-report", mapOf("success" to "false"))
          log.error("Prisoner contacts reconciliation report failed", it)
        }
    }
  }

  @PutMapping("/contact-person/person-contact/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun generatePersonContactReconciliationReport() {
    telemetryClient.trackEvent(
      "$TELEMETRY_PERSON_PREFIX-requested",
      mapOf(),
    )

    reportScope.launch {
      runCatching { reconciliationService.generatePersonContactReconciliationReport() }
        .onSuccess {
          telemetryClient.trackEvent(
            "$TELEMETRY_PERSON_PREFIX-report",
            mapOf(
              "contacts-count" to it.itemsChecked.toString(),
              "pages-count" to it.pagesChecked.toString(),
              "mismatch-count" to it.mismatches.size.toString(),
              "success" to "true",
            ) + it.mismatches.asPersonMap(),
          )
        }
        .onFailure {
          telemetryClient.trackEvent("$TELEMETRY_PERSON_PREFIX-report", mapOf("success" to "false"))
          log.error("Prisoner contacts reconciliation report failed", it)
        }
    }
  }

  @PreAuthorize("hasAnyRole('MIGRATE_CONTACTPERSON', 'MIGRATE_NOMIS_SYSCON', 'NOMIS_CONTACTPERSONS')")
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

// just take the first 10 personIds that fail else telemetry will not be written at all if attribute is too large
private fun List<MismatchPersonContacts>.asPersonMap(): Pair<String, String> = "personIds" to this.map { it.personId }.take(10).joinToString()
