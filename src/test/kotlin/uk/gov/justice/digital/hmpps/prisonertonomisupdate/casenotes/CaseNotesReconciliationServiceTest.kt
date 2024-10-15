package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.model.CaseNote
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CaseNoteAmendment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PrisonerCaseNotesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDateTime

private const val OFFENDER_NO = "A3456GH"
private const val DPS_CASE_NOTE_ID = "12345678-0000-0000-0000-000011112222"

class CaseNotesReconciliationServiceTest {

  private val caseNotesApiService: CaseNotesDpsApiService = mock()
  private val caseNotesNomisApiService: CaseNotesNomisApiService = mock()
  private val nomisApiService: NomisApiService = mock()
  private val telemetryClient: TelemetryClient = mock()

  val nomisPrisoner = PrisonerCaseNotesResponse(
    caseNotes = listOf(
      CaseNoteResponse(
        caseNoteId = 1L,
        bookingId = 1L,
        caseNoteType = CodeDescription("CODE", "desc"),
        caseNoteSubType = CodeDescription("SUBCODE", "desc"),
        authorStaffId = 101L,
        authorUsername = "USER",
        authorLastName = "SMITH",
        caseNoteText = "the actual text",
        amendments = listOf(
          CaseNoteAmendment(
            text = "the amendment text",
            authorUsername = "AMUSER",
            createdDateTime = "2024-01-01T01:02:03",
            sourceSystem = CaseNoteAmendment.SourceSystem.DPS,
          ),
        ),
        occurrenceDateTime = "2024-01-01T01:02:03",
        createdDatetime = "notused",
        createdUsername = "notused",
        sourceSystem = CaseNoteResponse.SourceSystem.DPS,
      ),
    ),
  )

  fun dpsPrisoner(
    type: String = "CODE",
    amendmentText: String = "the amendment text",
    amendments: List<uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.model.CaseNoteAmendment> = listOf(
      uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.model.CaseNoteAmendment(
        additionalNoteText = amendmentText,
        authorUserName = "AMUSER",
        authorName = "notused",
        creationDateTime = LocalDateTime.parse("2024-01-01T01:02:03"),
      ),
    ),
  ) =
    listOf(
      CaseNote(
        caseNoteId = DPS_CASE_NOTE_ID,
        text = "the actual text",
        type = type,
        subType = "SUBCODE",
        offenderIdentifier = OFFENDER_NO,
        typeDescription = "notused",
        subTypeDescription = "notused",
        source = "notused",
        creationDateTime = LocalDateTime.now(),
        occurrenceDateTime = LocalDateTime.parse("2024-01-01T01:02:03"),
        authorName = "notused",
        authorUserId = "notused",
        authorUsername = "USER",
        eventId = 1234L,
        sensitive = false,
        amendments = amendments,
        systemGenerated = false,
        legacyId = 1L,
      ),
    )

  private val caseNotesReconciliationService =
    CaseNotesReconciliationService(telemetryClient, caseNotesApiService, caseNotesNomisApiService, nomisApiService, 10)

  @Nested
  inner class CheckMatch {

    @Test
    fun `will not report mismatch where details match`() = runTest {
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(nomisPrisoner)
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(dpsPrisoner())

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isNull()
    }

    @Test
    fun `mismatch in type`() = runTest {
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(nomisPrisoner)
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        dpsPrisoner(type = "OTHER"),
      )

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isNotNull()
    }

    @Test
    fun `mismatch of amendments`() = runTest {
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(nomisPrisoner)
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        dpsPrisoner(amendments = emptyList()),
      )

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isNotNull()
    }

    @Test
    fun `mismatch within amendment`() = runTest {
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(nomisPrisoner)
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        dpsPrisoner(amendmentText = "discrepant"),
      )

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isNotNull()
    }

    @Test
    fun `will continue after a Nomis api error`() = runTest {
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenThrow(RuntimeException("test"))

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isNull()

      verify(telemetryClient).trackEvent(
        "casenotes-reports-reconciliation-mismatch-error",
        mapOf("offenderNo" to OFFENDER_NO, "error" to "test"),
        null,
      )
    }
  }
}
