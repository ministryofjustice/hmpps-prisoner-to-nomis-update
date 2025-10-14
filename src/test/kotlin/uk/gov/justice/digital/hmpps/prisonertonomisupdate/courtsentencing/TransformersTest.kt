package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CaseReferenceLegacyData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingApiExtension.Companion.reconciliationCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingApiExtension.Companion.reconciliationCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseIdentifier
import java.time.LocalDate
import java.time.LocalDateTime

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
  }
}
