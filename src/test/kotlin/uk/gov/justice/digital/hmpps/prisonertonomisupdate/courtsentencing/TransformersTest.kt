package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CaseReferenceLegacyData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationNextCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingApiExtension.Companion.reconciliationChargeWithoutSentence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingApiExtension.Companion.reconciliationCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingApiExtension.Companion.reconciliationCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseIdentifier
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class TransformersTest {
  @Nested
  inner class ReconciliationCourtCaseToCourtCaseRepairRequest {
    @Test
    fun `will copy core data`() {
      val nomisCourCase = reconciliationCourtCase(active = false).toCourtCaseRepairRequest()

      assertThat(nomisCourCase.status).isEqualTo("I")
      assertThat(nomisCourCase.legalCaseType).isEqualTo("NE")
    }

    @Test
    fun `will copy case references`() {
      val nomisCourCase = reconciliationCourtCase(
        caseReferences = listOf(
          CaseReferenceLegacyData(
            offenderCaseReference = "ABC123",
            updatedDate = LocalDateTime.parse("2021-01-01T10:00"),
            source = CaseReferenceLegacyData.Source.NOMIS,
          ),
          CaseReferenceLegacyData(
            offenderCaseReference = "DEF123",
            updatedDate = LocalDateTime.parse("2020-01-01T10:00"),
            source = CaseReferenceLegacyData.Source.NOMIS,
          ),
          CaseReferenceLegacyData(
            offenderCaseReference = "GHI123",
            updatedDate = LocalDateTime.parse("2022-01-01T10:00"),
            source = CaseReferenceLegacyData.Source.NOMIS,
          ),
        ),
      ).toCourtCaseRepairRequest()

      assertThat(nomisCourCase.caseReferences?.caseIdentifiers).hasSize(3)
      assertThat(nomisCourCase.caseReferences?.caseIdentifiers).containsExactlyInAnyOrder(
        CaseIdentifier(reference = "ABC123", createdDate = LocalDateTime.parse("2021-01-01T10:00")),
        CaseIdentifier(reference = "DEF123", createdDate = LocalDateTime.parse("2020-01-01T10:00")),
        CaseIdentifier(reference = "GHI123", createdDate = LocalDateTime.parse("2022-01-01T10:00")),
      )
    }

    @Test
    fun `will use first appearance date for court date`() {
      val nomisCourCase = reconciliationCourtCase(
        appearances = listOf(
          reconciliationCourtAppearance(appearanceDate = LocalDate.parse("2021-01-01")),
          reconciliationCourtAppearance(appearanceDate = LocalDate.parse("2020-01-01")),
          reconciliationCourtAppearance(appearanceDate = LocalDate.parse("2022-01-01")),
        ),
      ).toCourtCaseRepairRequest()

      assertThat(nomisCourCase.startDate).isEqualTo(LocalDate.parse("2020-01-01"))
    }

    @Test
    fun `will use first appearance court`() {
      val nomisCourCase = reconciliationCourtCase(
        appearances = listOf(
          reconciliationCourtAppearance(appearanceDate = LocalDate.parse("2021-01-01")).copy(courtCode = "MDI"),
          reconciliationCourtAppearance(appearanceDate = LocalDate.parse("2020-01-01")).copy(courtCode = "WWI"),
          reconciliationCourtAppearance(appearanceDate = LocalDate.parse("2022-01-01")).copy(courtCode = "BXI"),
        ),
      ).toCourtCaseRepairRequest()

      assertThat(nomisCourCase.courtId).isEqualTo("WWI")
    }

    @Test
    fun `will copy core appearance data`() {
      val nomisCourCase = reconciliationCourtCase(
        appearances = listOf(
          reconciliationCourtAppearance().copy(
            appearanceDate = LocalDate.parse("2021-01-01"),
            appearanceTime = "10:30",
            courtCode = "MDI",
            nomisOutcomeCode = "4001",
            nextCourtAppearance = null,
          ),
        ),
      ).toCourtCaseRepairRequest()

      assertThat(nomisCourCase.courtAppearances).hasSize(1)

      with(nomisCourCase.courtAppearances[0]) {
        assertThat(eventDateTime).isEqualTo(LocalDateTime.parse("2021-01-01T10:30"))
        assertThat(courtId).isEqualTo("MDI")
        assertThat(outcomeReasonCode).isEqualTo("4001")
        assertThat(nextEventDateTime).isNull()
        assertThat(nextCourtId).isNull()
      }
    }

    @Test
    fun `will copy next court appearance data`() {
      val nomisCourCase = reconciliationCourtCase(
        appearances = listOf(
          reconciliationCourtAppearance().copy(
            nextCourtAppearance = ReconciliationNextCourtAppearance(
              appearanceDate = LocalDate.parse("2021-02-03"),
              courtId = "WWI",
              appearanceTime = "10:30",
            ),
          ),
          reconciliationCourtAppearance().copy(
            nextCourtAppearance = ReconciliationNextCourtAppearance(
              appearanceDate = LocalDate.parse("2021-03-03"),
              courtId = "BXI",
              appearanceTime = null,
            ),
          ),
        ),
      ).toCourtCaseRepairRequest()

      assertThat(nomisCourCase.courtAppearances).hasSize(2)

      with(nomisCourCase.courtAppearances[0]) {
        assertThat(nextEventDateTime).isEqualTo(LocalDateTime.parse("2021-02-03T10:30"))
        assertThat(nextCourtId).isEqualTo("WWI")
      }
      with(nomisCourCase.courtAppearances[1]) {
        assertThat(nextEventDateTime).isEqualTo(LocalDateTime.parse("2021-03-03T00:00"))
        assertThat(nextCourtId).isEqualTo("BXI")
      }
    }

    @Test
    fun `will (incorrectly) hard code courtEventType`() {
      val nomisCourCase = reconciliationCourtCase(
        appearances = listOf(
          reconciliationCourtAppearance().copy(
            nextCourtAppearance = ReconciliationNextCourtAppearance(
              appearanceDate = LocalDate.parse("2021-03-03"),
              courtId = "BXI",
            ),
          ),
        ),
      ).toCourtCaseRepairRequest()

      assertThat(nomisCourCase.courtAppearances).hasSize(1)

      with(nomisCourCase.courtAppearances[0]) {
        // TODO: DPS API change to pass back appearance type
        assertThat(courtEventType).isEqualTo("CRT")
      }
    }

    @Test
    fun `will copy charge data for appearance`() {
      val nomisCourCase = reconciliationCourtCase(
        appearances = listOf(
          reconciliationCourtAppearance(
            charges = listOf(
              reconciliationChargeWithoutSentence().copy(
                chargeUuid = UUID.fromString("12345678-1234-1234-1234-123456789abc"),
                offenceStartDate = LocalDate.parse("2021-01-01"),
                offenceEndDate = null,
                nomisOutcomeCode = null,
              ),
              reconciliationChargeWithoutSentence().copy(
                chargeUuid = UUID.fromString("87654321-1234-1234-1234-123456789abc"),
                offenceStartDate = LocalDate.parse("2021-01-01"),
                offenceEndDate = LocalDate.parse("2022-01-01"),
                nomisOutcomeCode = "4001",
              ),
            ),
          ),
        ),
      ).toCourtCaseRepairRequest()

      assertThat(nomisCourCase.courtAppearances).hasSize(1)
      assertThat(nomisCourCase.courtAppearances[0].courtEventCharges).hasSize(2)

      with(nomisCourCase.courtAppearances[0].courtEventCharges[0]) {
        assertThat(id).isEqualTo("12345678-1234-1234-1234-123456789abc")
        assertThat(offenceDate).isEqualTo(LocalDate.parse("2021-01-01"))
        assertThat(offenceEndDate).isNull()
        assertThat(resultCode1).isNull()
      }
      with(nomisCourCase.courtAppearances[0].courtEventCharges[1]) {
        assertThat(id).isEqualTo("87654321-1234-1234-1234-123456789abc")
        assertThat(offenceDate).isEqualTo(LocalDate.parse("2021-01-01"))
        assertThat(offenceEndDate).isEqualTo(LocalDate.parse("2022-01-01"))
        assertThat(resultCode1).isEqualTo("4001")
      }
    }

    @Test
    fun `will copy charge data for court case`() {
      val nomisCourCase = reconciliationCourtCase(
        appearances = listOf(
          reconciliationCourtAppearance(
            appearanceDate = LocalDate.parse("2021-01-01"),
            charges = listOf(
              reconciliationChargeWithoutSentence().copy(
                chargeUuid = UUID.fromString("12345678-1234-1234-1234-123456789abc"),
                offenceStartDate = LocalDate.parse("2021-01-01"),
                offenceEndDate = null,
                nomisOutcomeCode = null,
                offenceCode = "TR11017",
              ),
              reconciliationChargeWithoutSentence().copy(
                chargeUuid = UUID.fromString("87654321-1234-1234-1234-123456789abc"),
                offenceStartDate = LocalDate.parse("2021-01-01"),
                offenceEndDate = LocalDate.parse("2022-01-01"),
                nomisOutcomeCode = "3001",
                offenceCode = "PR52028A",
              ),
            ),
          ),
          reconciliationCourtAppearance(
            appearanceDate = LocalDate.parse("2021-02-01"),
            charges = listOf(
              reconciliationChargeWithoutSentence().copy(
                chargeUuid = UUID.fromString("12345678-1234-1234-1234-123456789abc"),
                offenceStartDate = LocalDate.parse("2021-01-02"),
                offenceEndDate = null,
                nomisOutcomeCode = null,
                offenceCode = "TR11017",
              ),
              reconciliationChargeWithoutSentence().copy(
                chargeUuid = UUID.fromString("87654321-1234-1234-1234-123456789abc"),
                offenceStartDate = LocalDate.parse("2021-01-01"),
                offenceEndDate = LocalDate.parse("2022-01-01"),
                nomisOutcomeCode = "4001",
                offenceCode = "PR52028A",
              ),
            ),
          ),
        ),
      ).toCourtCaseRepairRequest()

      assertThat(nomisCourCase.offenderCharges).hasSize(2)
      assertThat(nomisCourCase.offenderCharges.find { it.offenceCode == "TR11017" }).isNotNull
      assertThat(nomisCourCase.offenderCharges.find { it.offenceCode == "PR52028A" }).isNotNull

      with(nomisCourCase.offenderCharges.find { it.offenceCode == "TR11017" }!!) {
        assertThat(id).isEqualTo("12345678-1234-1234-1234-123456789abc")
        assertThat(offenceCode).isEqualTo("TR11017")
        assertThat(offenceDate).isEqualTo(LocalDate.parse("2021-01-02"))
        assertThat(offenceEndDate).isNull()
        assertThat(resultCode1).isNull()
      }
      with(nomisCourCase.offenderCharges.find { it.offenceCode == "PR52028A" }!!) {
        assertThat(id).isEqualTo("87654321-1234-1234-1234-123456789abc")
        assertThat(offenceDate).isEqualTo(LocalDate.parse("2021-01-01"))
        assertThat(offenceEndDate).isEqualTo(LocalDate.parse("2022-01-01"))
        assertThat(resultCode1).isEqualTo("4001")
      }
    }
  }
}
