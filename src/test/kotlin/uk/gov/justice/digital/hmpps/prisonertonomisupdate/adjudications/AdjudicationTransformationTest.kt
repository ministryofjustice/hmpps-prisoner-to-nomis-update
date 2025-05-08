package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.IncidentDetailsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.IncidentRoleDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.IncidentStatementDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OffenceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OffenceRuleDetailsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OffenceRuleDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedDamageDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedEvidenceDto
import java.time.LocalDateTime
import java.util.UUID

class AdjudicationTransformationTest {
  @Test
  fun `will copy core incident details`() {
    val dpsAdjudication = dpsAdjudication().copy(
      reportedAdjudication = dpsAdjudication().reportedAdjudication.copy(
        chargeNumber = "1234567",
        incidentDetails = IncidentDetailsDto(
          locationId = 543311,
          locationUuid = UUID.randomUUID(),
          dateTimeOfIncident = LocalDateTime.parse("2023-07-27T23:30:00"),
          dateTimeOfDiscovery = LocalDateTime.parse("2023-07-28T01:12:00"),
          handoverDeadline = LocalDateTime.parse("2023-07-30T01:12:00"),
        ),
        createdByUserId = "GBROWN",
        createdDateTime = LocalDateTime.parse("2023-07-29T12:01:15"),
        originatingAgencyId = "WWI",
        incidentStatement = IncidentStatementDto(
          statement = "A fight broke out and there was damage",
          completed = true,
        ),
      ),
    )
    val nomisAdjudication = dpsAdjudication.toNomisAdjudication()

    assertThat(nomisAdjudication.incident.reportingStaffUsername).isEqualTo("GBROWN")
    assertThat(nomisAdjudication.incident.incidentDate).isEqualTo("2023-07-28")
    assertThat(nomisAdjudication.incident.incidentTime).isEqualTo("01:12")
    assertThat(nomisAdjudication.incident.reportedDate).isEqualTo("2023-07-29")
    assertThat(nomisAdjudication.incident.reportedTime).isEqualTo("12:01")
    assertThat(nomisAdjudication.incident.internalLocationId).isEqualTo(543311)
    assertThat(nomisAdjudication.incident.prisonId).isEqualTo("WWI")
    assertThat(nomisAdjudication.incident.details).isEqualTo("A fight broke out and there was damage")
  }

  @Test
  fun `will copy charge details`() {
    val dpsAdjudication = dpsAdjudication().copy(
      reportedAdjudication = dpsAdjudication().reportedAdjudication.copy(
        offenceDetails = OffenceDto(
          offenceCode = 1002,
          offenceRule = OffenceRuleDto(
            paragraphNumber = "1",
            paragraphDescription = "Commits any assault",
            nomisCode = "51:1B",
            withOthersNomisCode = "51:25D",
          ),
          victimPrisonersNumber = "A1234AA",
          protectedCharacteristics = emptyList(),
        ),
      ),
    )
    val nomisAdjudication = dpsAdjudication.toNomisAdjudication()

    assertThat(nomisAdjudication.charges).hasSize(1)
    assertThat(nomisAdjudication.charges[0].offenceCode).isEqualTo("51:1B")
  }

  @Test
  fun `will use paragraph number if there is no NOMIS charge code`() {
    val dpsAdjudication = dpsAdjudication().copy(
      reportedAdjudication = dpsAdjudication().reportedAdjudication.copy(
        offenceDetails = OffenceDto(
          offenceCode = 1002,
          offenceRule = OffenceRuleDto(
            paragraphNumber = "51:12A",
            paragraphDescription = "Commits any assault",
            nomisCode = null,
          ),
          protectedCharacteristics = emptyList(),
        ),
      ),
    )

    val nomisAdjudication = dpsAdjudication.toNomisAdjudication()

    assertThat(nomisAdjudication.charges).hasSize(1)
    assertThat(nomisAdjudication.charges[0].offenceCode).isEqualTo("51:12A")
  }

  @Test
  fun `will use the withOther offence code if this adjudication is the other prisoner in an incident`() {
    val dpsAdjudication = dpsAdjudication().copy(
      reportedAdjudication = dpsAdjudication().reportedAdjudication.copy(
        incidentRole = IncidentRoleDto(
          roleCode = "25c",
          offenceRule = OffenceRuleDetailsDto(
            paragraphNumber = "25(c)",
            paragraphDescription = "Assists another prisoner to commit, or to attempt to commit, any of the foregoing offences:",
          ),
          associatedPrisonersName = "A4323ZA",
        ),
        offenceDetails = OffenceDto(
          offenceCode = 1002,
          offenceRule = OffenceRuleDto(
            paragraphNumber = "1",
            paragraphDescription = "Commits any assault",
            nomisCode = "51:1B",
            withOthersNomisCode = "51:25D",
          ),
          victimPrisonersNumber = "A1234AA",
          protectedCharacteristics = emptyList(),
        ),
      ),
    )
    val nomisAdjudication = dpsAdjudication.toNomisAdjudication()

    assertThat(nomisAdjudication.charges).hasSize(1)
    assertThat(nomisAdjudication.charges[0].offenceCode).isEqualTo("51:25D")
  }

  @Test
  fun `use paragraph number if withOther offence code is missing`() {
    val dpsAdjudication = dpsAdjudication().copy(
      reportedAdjudication = dpsAdjudication().reportedAdjudication.copy(
        incidentRole = IncidentRoleDto(
          roleCode = "25c",
          offenceRule = OffenceRuleDetailsDto(
            paragraphNumber = "25(c)",
            paragraphDescription = "Assists another prisoner to commit, or to attempt to commit, any of the foregoing offences:",
          ),
          associatedPrisonersName = "A4323ZA",
        ),
        offenceDetails = OffenceDto(
          offenceCode = 1002,
          offenceRule = OffenceRuleDto(
            paragraphNumber = "51:1B",
            paragraphDescription = "Commits any assault",
            nomisCode = "51:1B",
            withOthersNomisCode = null,
          ),
          victimPrisonersNumber = "A1234AA",
          protectedCharacteristics = emptyList(),
        ),
      ),
    )

    val nomisAdjudication = dpsAdjudication.toNomisAdjudication()

    assertThat(nomisAdjudication.charges).hasSize(1)
    assertThat(nomisAdjudication.charges[0].offenceCode).isEqualTo("51:1B")
  }

  @Test
  fun `evidence is copied and mapped`() {
    val dpsAdjudication = dpsAdjudication().copy(
      reportedAdjudication = dpsAdjudication().reportedAdjudication.copy(
        evidence = listOf(
          ReportedEvidenceDto(code = ReportedEvidenceDto.Code.PHOTO, details = "Photo of evidence", reporter = "GBROWN"),
          ReportedEvidenceDto(code = ReportedEvidenceDto.Code.BAGGED_AND_TAGGED, details = "Bag of evidence", reporter = "GBROWN"),
          ReportedEvidenceDto(code = ReportedEvidenceDto.Code.CCTV, details = "Video of evidence", reporter = "GBROWN"),
          ReportedEvidenceDto(code = ReportedEvidenceDto.Code.BODY_WORN_CAMERA, details = "Body video of evidence", reporter = "GBROWN"),
        ),
      ),
    )
    val nomisAdjudication = dpsAdjudication.toNomisAdjudication()

    assertThat(nomisAdjudication.evidence).hasSize(4)
    assertThat(nomisAdjudication.evidence[0].typeCode.name).isEqualTo("PHOTO")
    assertThat(nomisAdjudication.evidence[0].detail).isEqualTo("Photo of evidence")
    assertThat(nomisAdjudication.evidence[1].typeCode.name).isEqualTo("EVI_BAG")
    assertThat(nomisAdjudication.evidence[1].detail).isEqualTo("Bag of evidence")
    assertThat(nomisAdjudication.evidence[2].typeCode.name).isEqualTo("OTHER")
    assertThat(nomisAdjudication.evidence[2].detail).isEqualTo("Video of evidence")
    assertThat(nomisAdjudication.evidence[3].typeCode.name).isEqualTo("OTHER")
    assertThat(nomisAdjudication.evidence[3].detail).isEqualTo("Body video of evidence")
  }

  @Test
  fun `repairs are copied and mapped`() {
    val dpsAdjudication = dpsAdjudication().copy(
      reportedAdjudication = dpsAdjudication().reportedAdjudication.copy(
        damages = listOf(
          ReportedDamageDto(code = ReportedDamageDto.Code.ELECTRICAL_REPAIR, details = "electrical repair", reporter = "GBROWN"),
          ReportedDamageDto(code = ReportedDamageDto.Code.CLEANING, details = "cleaning", reporter = "GBROWN"),
          ReportedDamageDto(code = ReportedDamageDto.Code.LOCK_REPAIR, details = "lock repair", reporter = "GBROWN"),
          ReportedDamageDto(code = ReportedDamageDto.Code.PLUMBING_REPAIR, details = "plumbing repair", reporter = "GBROWN"),
          ReportedDamageDto(code = ReportedDamageDto.Code.FURNITURE_OR_FABRIC_REPAIR, details = "furniture repair", reporter = "GBROWN"),
          ReportedDamageDto(code = ReportedDamageDto.Code.REDECORATION, details = "redecoration", reporter = "GBROWN"),
          ReportedDamageDto(code = ReportedDamageDto.Code.REPLACE_AN_ITEM, details = "replace and item", reporter = "GBROWN"),
        ),
      ),
    )
    val nomisAdjudication = dpsAdjudication.toNomisAdjudication()

    assertThat(nomisAdjudication.incident.repairs).hasSize(7)
    assertThat(nomisAdjudication.incident.repairs[0].typeCode.name).isEqualTo("ELEC")
    assertThat(nomisAdjudication.incident.repairs[0].comment).isEqualTo("electrical repair")
    assertThat(nomisAdjudication.incident.repairs[1].typeCode.name).isEqualTo("CLEA")
    assertThat(nomisAdjudication.incident.repairs[1].comment).isEqualTo("cleaning")
    assertThat(nomisAdjudication.incident.repairs[2].typeCode.name).isEqualTo("LOCK")
    assertThat(nomisAdjudication.incident.repairs[2].comment).isEqualTo("lock repair")
    assertThat(nomisAdjudication.incident.repairs[3].typeCode.name).isEqualTo("PLUM")
    assertThat(nomisAdjudication.incident.repairs[3].comment).isEqualTo("plumbing repair")
    assertThat(nomisAdjudication.incident.repairs[4].typeCode.name).isEqualTo("FABR")
    assertThat(nomisAdjudication.incident.repairs[4].comment).isEqualTo("furniture repair")
    assertThat(nomisAdjudication.incident.repairs[5].typeCode.name).isEqualTo("DECO")
    assertThat(nomisAdjudication.incident.repairs[5].comment).isEqualTo("redecoration")
    assertThat(nomisAdjudication.incident.repairs[6].typeCode.name).isEqualTo("DECO")
    assertThat(nomisAdjudication.incident.repairs[6].comment).isEqualTo("replace and item")
  }

  @Test
  fun `if there is a prisoner victims it is copied`() {
    val dpsAdjudication = dpsAdjudication().copy(
      reportedAdjudication = dpsAdjudication().reportedAdjudication.copy(
        offenceDetails = OffenceDto(
          offenceCode = 1002,
          offenceRule = OffenceRuleDto(
            paragraphNumber = "1",
            paragraphDescription = "Assists another prisoner to commit, or to attempt to commit, any of the foregoing offences:",
            nomisCode = "51:1B",
          ),
          victimPrisonersNumber = "A1234AA",
          protectedCharacteristics = emptyList(),
        ),
      ),
    )
    val nomisAdjudication = dpsAdjudication.toNomisAdjudication()
    assertThat(nomisAdjudication.incident.prisonerVictimsOffenderNumbers).hasSize(1).containsExactly("A1234AA")
    assertThat(nomisAdjudication.incident.staffVictimsUsernames).hasSize(0)
  }

  @Test
  fun `victim not copied if they are also the suspect (NOMIS constraints do not allow this)`() {
    val dpsAdjudication = dpsAdjudication().copy(
      reportedAdjudication = dpsAdjudication().reportedAdjudication.copy(
        prisonerNumber = "A1234AA",
        offenceDetails = OffenceDto(
          offenceCode = 1002,
          offenceRule = OffenceRuleDto(
            paragraphNumber = "1",
            paragraphDescription = "Assists another prisoner to commit, or to attempt to commit, any of the foregoing offences:",
            nomisCode = "51:1B",
          ),
          victimPrisonersNumber = "A1234AA",
          protectedCharacteristics = emptyList(),
        ),
      ),
    )
    val nomisAdjudication = dpsAdjudication.toNomisAdjudication()
    assertThat(nomisAdjudication.incident.prisonerVictimsOffenderNumbers).hasSize(0)
  }

  @Test
  fun `if there is a staff victims it is copied`() {
    val dpsAdjudication = dpsAdjudication().copy(
      reportedAdjudication = dpsAdjudication().reportedAdjudication.copy(
        offenceDetails = OffenceDto(
          offenceCode = 1002,
          offenceRule = OffenceRuleDto(
            paragraphNumber = "1",
            paragraphDescription = "Assists another prisoner to commit, or to attempt to commit, any of the foregoing offences:",
            nomisCode = "51:1B",
          ),
          victimStaffUsername = "J.SMITH",
          protectedCharacteristics = emptyList(),
        ),
      ),
    )
    val nomisAdjudication = dpsAdjudication.toNomisAdjudication()
    assertThat(nomisAdjudication.incident.prisonerVictimsOffenderNumbers).hasSize(0)
    assertThat(nomisAdjudication.incident.staffVictimsUsernames).hasSize(1).containsExactly("J.SMITH")
  }
}

private fun dpsAdjudication() = ReportedAdjudicationResponse(
  reportedAdjudication = ReportedAdjudicationDto(
    chargeNumber = "1234567",
    prisonerNumber = "A1234AK",
    gender = ReportedAdjudicationDto.Gender.FEMALE,
    incidentDetails = IncidentDetailsDto(
      locationId = 543311,
      dateTimeOfIncident = LocalDateTime.parse("2023-07-27T23:30:00"),
      dateTimeOfDiscovery = LocalDateTime.parse("2023-07-28T01:12:00"),
      handoverDeadline = LocalDateTime.parse("2023-07-30T01:12:00"),
      locationUuid = UUID.randomUUID(),
    ),
    isYouthOffender = false,
    incidentRole = IncidentRoleDto(),
    offenceDetails = OffenceDto(
      offenceCode = 1002,
      offenceRule = OffenceRuleDto(
        paragraphNumber = "1",
        paragraphDescription = "Assists another prisoner to commit, or to attempt to commit, any of the foregoing offences:",
        nomisCode = "51:1B",
        withOthersNomisCode = "51:25D",
      ),
      victimPrisonersNumber = "A8349DY",
      protectedCharacteristics = emptyList(),
    ),
    incidentStatement = IncidentStatementDto(
      statement = "A fight broke out and there was damage",
      completed = true,
    ),
    createdByUserId = "ABARTLETT",
    createdDateTime = LocalDateTime.parse("2023-07-28T12:00:15.94454"),
    status = ReportedAdjudicationDto.Status.UNSCHEDULED,
    damages = emptyList(),
    evidence = emptyList(),
    witnesses = emptyList(),
    hearings = emptyList(),
    disIssueHistory = emptyList(),
    outcomes = emptyList(),
    punishments = emptyList(),
    punishmentComments = emptyList(),
    outcomeEnteredInNomis = false,
    originatingAgencyId = "MDI",
    reviewedByUserId = "ABARTLETT",
    statusReason = null,
    statusDetails = null,
    issuingOfficer = null,
    dateTimeOfIssue = null,
    dateTimeOfFirstHearing = null,
    overrideAgencyId = null,
    transferableActionsAllowed = null,
    linkedChargeNumbers = emptyList(),
    canActionFromHistory = false,
  ),
)
