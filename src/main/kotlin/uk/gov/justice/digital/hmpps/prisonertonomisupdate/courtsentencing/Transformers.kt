package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseIdentifier
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseIdentifierRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtAppearanceRepairRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtCaseRepairRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtEventChargeRepairRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderChargeRepairRequest
import java.time.LocalDateTime
import java.time.LocalTime

fun ReconciliationCourtCase.toCourtCaseRepairRequest(): CourtCaseRepairRequest = CourtCaseRepairRequest(
  startDate = this.appearances.minOf { it.appearanceDate },
  legalCaseType = "NE",
  courtId = this.appearances.minBy { it.appearanceDate }.courtCode,
  status = if (this.active) {
    "A"
  } else {
    "I"
  },
  courtAppearances = this.appearances.map { appearance ->
    CourtAppearanceRepairRequest(
      eventDateTime = LocalDateTime.of(
        appearance.appearanceDate,
        LocalTime.parse(appearance.appearanceTime),
      ),
      // TODO  required DPS API change to get appearance type
      courtEventType = "CRT",
      courtId = appearance.courtCode,
      outcomeReasonCode = appearance.nomisOutcomeCode,
      nextEventDateTime = appearance.nextCourtAppearance?.let { next ->
        next.appearanceTime?.let {
          LocalDateTime.of(
            next.appearanceDate,
            LocalTime.parse(it),
          )
        } ?: LocalDateTime.of(
          next.appearanceDate,
          LocalTime.MIDNIGHT,
        )
      },
      courtEventCharges = appearance.charges.map { charge ->
        CourtEventChargeRepairRequest(
          id = charge.chargeUuid.toString(),
          offenceDate = charge.offenceStartDate!!,
          offenceEndDate = charge.offenceEndDate,
          resultCode1 = charge.nomisOutcomeCode,
        )
      },
      nextCourtId = appearance.nextCourtAppearance?.courtId,
    )
  },
  offenderCharges = appearances
    .flatMap { appearance -> appearance.charges }
    .map { charge -> charge.chargeUuid }
    .distinct()
    .map { chargeUuid ->
      // find the last appearance with this charge in it
      val lastAppearanceForCharge = appearances.filter { appearance -> appearance.charges.find { charge -> charge.chargeUuid == chargeUuid } != null }.maxBy { it.appearanceDate.atTime(LocalTime.parse(it.appearanceTime)) }
      val chargeOnLastAppearance = lastAppearanceForCharge.charges.find { charge -> charge.chargeUuid == chargeUuid }!!
      OffenderChargeRepairRequest(
        id = chargeUuid.toString(),
        offenceDate = chargeOnLastAppearance.offenceStartDate!!,
        offenceEndDate = chargeOnLastAppearance.offenceEndDate,
        resultCode1 = chargeOnLastAppearance.nomisOutcomeCode,
        offenceCode = chargeOnLastAppearance.offenceCode,
      )
    },
  caseReferences = CaseIdentifierRequest(
    caseIdentifiers = this.courtCaseLegacyData!!.caseReferences.map {
      CaseIdentifier(
        reference = it.offenderCaseReference,
        createdDate = it.updatedDate,
      )
    },
  ),
)
