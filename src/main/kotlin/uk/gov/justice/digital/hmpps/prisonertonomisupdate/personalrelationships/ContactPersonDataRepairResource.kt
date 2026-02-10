package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Contact Person Repair Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_TO_NOMIS__SYNCHRONISATION__RW')")
class ContactPersonDataRepairResource(
  private val contactPersonService: ContactPersonService,
  private val telemetryClient: TelemetryClient,
) {
  @PostMapping("/contacts/{contactId}/resynchronise")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Creates contact aka person data from DPS back to NOMIS. This will create the contact and any support child entities including the prisoner relationship that are created in DPS create contact flow. Currently only supports creating a contact without restrictions. When mapping already exists for entity or child no update will be applied",
    description = "Used when an unexpected event has happened in DPS, for instance no domain events published after create contact that has resulted in the NOMIS data drifting from DPS, so emergency use only. Requires ROLE_PRISONER_TO_NOMIS__SYNCHRONISATION__RW",
  )
  suspend fun repairContact(
    @PathVariable contactId: Long,
  ) {
    contactPersonService.createContactForRepair(contactId)
    telemetryClient.trackEvent(
      "to-nomis-synch-contactperson-resynchronisation-repair",
      mapOf(
        "dpsContactId" to contactId.toString(),
      ),
      null,
    )
  }

  @PutMapping("/contacts/{contactId}/prisoner-contact/{prisonerContactId}/resynchronise")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Update prisoner contact aka contact data from DPS back to NOMIS. This will update the prisoner contact",
    description = "Used when an unexpected event has happened in DPS, for instance no domain events published after a prisoner contact that has resulted in the NOMIS data drifting from DPS, so emergency use only. Requires ROLE_PRISONER_TO_NOMIS__SYNCHRONISATION__RW",
  )
  suspend fun repairPrisonerContact(
    @PathVariable contactId: Long,
    @PathVariable prisonerContactId: Long,
  ) {
    contactPersonService.repairPrisonerContact(
      contactId = contactId,
      prisonerContactId = prisonerContactId,
    )
    telemetryClient.trackEvent(
      "to-nomis-synch-contactperson-prisoner-contact-resynchronisation-repair",
      mapOf(
        "dpsContactId" to contactId.toString(),
        "dpsPrisonerContactId" to prisonerContactId.toString(),
      ),
      null,
    )
  }
}
