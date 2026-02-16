package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.model.CaseNote
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AllPrisonerCaseNoteMappingsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CaseNoteMappingDto.MappingType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseNoteAmendment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerCaseNotesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDateTime
import java.time.LocalDateTime.parse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.model.CaseNoteAmendment as DpsCaseNoteAmendment

private const val OFFENDER_NO = "A3456GH"
private const val DPS_CASE_NOTE_ID = "12345678-0000-0000-0000-000011112222"
private const val TWO_UNICODE_CHARS = "⌘⌥"

class CaseNotesReconciliationServiceTest {

  private val caseNotesApiService: CaseNotesDpsApiService = mock()
  private val caseNotesNomisApiService: CaseNotesNomisApiService = mock()
  private val nomisApiService: NomisApiService = mock()
  private val caseNotesMappingApiService: CaseNotesMappingApiService = mock()
  private val telemetryClient: TelemetryClient = mock()

  fun nomisPrisoner(
    type: String = "CODE",
    subType: String = "SUBCODE",
    authorUsername: String = "USER",
    authorUsernames: List<String>? = listOf("USER"),
    creationDateTime: LocalDateTime = parse("2024-02-02T01:02:03"),
    text: String = "the actual text",
    amendmentText: String = "the amendment text",
    amendments: List<CaseNoteAmendment> = listOf(
      CaseNoteAmendment(
        text = amendmentText,
        authorUsername = "AMUSER",
        createdDateTime = parse("2024-01-01T01:02:03"),
        sourceSystem = CaseNoteAmendment.SourceSystem.NOMIS,
      ),
    ),
  ) = PrisonerCaseNotesResponse(
    listOf(
      templateNomisCaseNote(
        caseNoteId = 1,
        bookingId = 1,
        type = type,
        subType = subType,
        authorUsername = authorUsername,
        authorUsernames = authorUsernames,
        creationDateTime = creationDateTime,
        text = text,
        amendmentText = amendmentText,
        amendments = amendments,
      ),
    ),
  )

  fun dpsPrisoner(
    type: String = "CODE",
    subType: String = "SUBCODE",
    creationDateTime: LocalDateTime = parse("2024-02-02T01:02:03.567"),
    text: String = "the actual text",
    amendmentText: String = "the amendment text",
    amendments: List<DpsCaseNoteAmendment> = listOf(
      DpsCaseNoteAmendment(
        additionalNoteText = amendmentText,
        authorUserName = "AMUSER",
        authorName = "notused",
        creationDateTime = parse("2024-01-01T01:02:03.456"),
      ),
    ),
  ) = listOf(
    templateDpsCaseNote(
      dpsId = DPS_CASE_NOTE_ID,
      prisonerNo = OFFENDER_NO,
      type = type,
      subType = subType,
      creationDateTime = creationDateTime,
      text = text,
      amendmentText = amendmentText,
      amendments = amendments,
    ),
  )

  private val caseNotesReconciliationService =
    CaseNotesReconciliationService(telemetryClient, caseNotesApiService, caseNotesNomisApiService, nomisApiService, caseNotesMappingApiService, 10)

  @Nested
  inner class CheckMatch {
    @BeforeEach
    fun beforeEach() = runTest {
      whenever(caseNotesMappingApiService.getByPrisoner(OFFENDER_NO)).thenReturn(
        AllPrisonerCaseNoteMappingsDto(listOf(templateMapping(1L, DPS_CASE_NOTE_ID, OFFENDER_NO))),
      )
    }

    @Test
    fun `will not report mismatch where details match`() = runTest {
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(nomisPrisoner())
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(dpsPrisoner())

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isNull()
    }

    @Test
    fun `mismatch in type`() = runTest {
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(nomisPrisoner())
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        dpsPrisoner(type = "OTHER"),
      )

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isEqualTo(
        MismatchCaseNote(
          offenderNo = "A3456GH",
          notes = listOf(
            "dpsCaseNote = {id=12345678-0000-0000-0000-000011112222, legacyId=-1, text-hash=128700130, type=OTHER, subType=SUBCODE, occurrenceDateTime=2024-01-01T01:02:03, creationDateTime=2024-02-02T01:02:03, authorUsername=USER}," +
              " nomisCaseNote = {id=1, legacyId=1, text-hash=128700130, type=CODE, subType=SUBCODE, occurrenceDateTime=2024-01-01T01:02:03, creationDateTime=2024-02-02T01:02:03, authorUsername=[USER]}",
          ),
        ),
      )
    }

    @Test
    fun `mismatch is logged`() = runTest {
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(nomisPrisoner())
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        dpsPrisoner(type = "OTHER"),
      )

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isNotNull()

      verify(telemetryClient).trackEvent(
        eq("casenotes-reports-reconciliation-mismatch"),

        check<MutableMap<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "offenderNo" to OFFENDER_NO,
              "1" to "dpsCaseNote = {id=12345678-0000-0000-0000-000011112222, legacyId=-1, text-hash=128700130, type=OTHER, subType=SUBCODE, occurrenceDateTime=2024-01-01T01:02:03, creationDateTime=2024-02-02T01:02:03, authorUsername=USER}," +
                " nomisCaseNote = {id=1, legacyId=1, text-hash=128700130, type=CODE, subType=SUBCODE, occurrenceDateTime=2024-01-01T01:02:03, creationDateTime=2024-02-02T01:02:03, authorUsername=[USER]}",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `truncated NOMIS case notes don't get marked as a mismatch`() = runTest {
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        PrisonerCaseNotesResponse(
          listOf(
            nomisPrisoner().caseNotes[0].copy(
              caseNoteText = "${"0123456789".repeat(397)}01234$SEE_DPS_REPLACEMENT",
              amendments = listOf(),
            ),
          ),
        ),
      )
      // this has an amendment in DPS, but will get ignored in the comparison as the case note is too long already
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        listOf(dpsPrisoner()[0].copy(text = "${"0123456789".repeat(400)}this is too long")),
      )

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isNull()
      verifyNoInteractions(telemetryClient)
    }

    @Test
    fun `truncated NOMIS case note amendments don't get marked as a mismatch`() = runTest {
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        PrisonerCaseNotesResponse(
          listOf(
            nomisPrisoner().caseNotes[0].let {
              it.copy(amendments = listOf(it.amendments[0].copy(text = "${"0123456789".repeat(390)}0$SEE_DPS_REPLACEMENT")))
            },
          ),
        ),
      )
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        listOf(
          dpsPrisoner()[0].let {
            it.copy(amendments = listOf(it.amendments[0].copy(additionalNoteText = "${"0123456789".repeat(400)}this is too long")))
          },
        ),
      )

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isNull()
      verifyNoInteractions(telemetryClient)
    }

    @Test
    fun `partially truncated NOMIS case note amendments don't get marked as a mismatch`() = runTest {
      val baseNote = "${"0123456789".repeat(393)}0"
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        PrisonerCaseNotesResponse(
          listOf(
            nomisPrisoner().caseNotes[0].copy(
              caseNoteText = "$baseNote ...[AMUSER updated the case notes on 2024/0$SEE_DPS_REPLACEMENT",
              amendments = listOf(),
            ),
          ),
        ),
      )
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        listOf(
          dpsPrisoner()[0].let {
            it.copy(
              text = baseNote,
              amendments = listOf(it.amendments[0].copy(additionalNoteText = "0123456789".repeat(2))),
            )
          },
        ),
      )

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isNull()
      verifyNoInteractions(telemetryClient)
    }

    @Test
    fun `text with unicode chars near the end DO NOT match with old algorithm`() = runTest {
      val oldAlgorithmDate = parse("2024-01-01T01:02:03")
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        nomisPrisoner(
          text = "${"0123456789".repeat(397)}01234$SEE_DPS_REPLACEMENT",
          creationDateTime = oldAlgorithmDate,
          amendments = listOf(),
        ),
      )
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        dpsPrisoner(
          text = "${"0123456789".repeat(397)}01234$TWO_UNICODE_CHARS this is too long, stretching over 25 chars",
          creationDateTime = oldAlgorithmDate,
          amendments = listOf(),
        ),
      )

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isNotNull()
    }

    @Test
    fun `text with unicode chars near the end DO match with new algorithm`() = runTest {
      val newAlgorithmDate = parse("2025-08-01T01:02:03")
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        nomisPrisoner(
          text = "${"0123456789".repeat(397)}01234$SEE_DPS_REPLACEMENT",
          creationDateTime = newAlgorithmDate,
          amendments = listOf(),
        ),
      )
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        dpsPrisoner(
          text = "${"0123456789".repeat(397)}01234$TWO_UNICODE_CHARS this is too long, stretching over 25 chars",
          creationDateTime = newAlgorithmDate,
          amendments = listOf(),
        ),
      )

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isNull()
      verifyNoInteractions(telemetryClient)
    }

    @Test
    fun `mismatch of amendments`() = runTest {
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(nomisPrisoner())
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        dpsPrisoner(amendments = emptyList()),
      )

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isNotNull()
    }

    @Test
    fun `mismatch within amendment`() = runTest {
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(nomisPrisoner())
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        dpsPrisoner(amendmentText = "discrepant"),
      )

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isNotNull()
    }

    @Test
    fun `Ignore APP-CNOTE mismatch`() = runTest {
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        nomisPrisoner(type = "CNOTE", subType = "OUTCOME"),
      )
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        dpsPrisoner(type = "APP", subType = "OUTCOME"),
      )

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isNull()
    }

    @Test
    fun `authorUsernames in Nomis list matches`() = runTest {
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        nomisPrisoner(authorUsername = "OTHER1", authorUsernames = listOf("OTHER1", "USER", "OTHER2")),
      )
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        dpsPrisoner(),
      )

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isNull()
    }

    @Test
    fun `will continue after a Nomis api error`() = runTest {
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(dpsPrisoner())
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenThrow(RuntimeException("test"))

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isNull()

      verify(telemetryClient).trackEvent(
        "casenotes-reports-reconciliation-mismatch-error",
        mapOf("offenderNo" to OFFENDER_NO, "error" to "test"),
        null,
      )
    }

    @Test
    fun `will not report a temporary error due to case note being created at the same time`() = runTest {
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO))
        .thenReturn(emptyList())
        .thenReturn(dpsPrisoner())
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(nomisPrisoner())

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isNull()
    }
  }

  @Nested
  inner class CheckMatchWithMerges {
    @BeforeEach
    fun beforeEach() = runTest {
      whenever(caseNotesMappingApiService.getByPrisoner(OFFENDER_NO)).thenReturn(
        AllPrisonerCaseNoteMappingsDto(
          listOf(
            templateMapping(1, DPS_CASE_NOTE_ID, OFFENDER_NO),
            templateMapping(2, DPS_CASE_NOTE_ID, OFFENDER_NO, 2),
            templateMapping(3, DPS_CASE_NOTE_ID, OFFENDER_NO, 3),
          ),
        ),
      )
    }

    @Test
    fun `will not report mismatch where details match with Merges`() = runTest {
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        PrisonerCaseNotesResponse(
          listOf(
            templateNomisCaseNote(1, 1),
            templateNomisCaseNote(2, 2),
            templateNomisCaseNote(3, 3),
          ),
        ),
      )
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(dpsPrisoner())

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isNull()
    }

    @Test
    fun `will report mismatch where details dont match with Merges`() = runTest {
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        PrisonerCaseNotesResponse(
          listOf(
            templateNomisCaseNote(1, 1),
            templateNomisCaseNote(2, 2),
            templateNomisCaseNote(3, 3, subType = "DIFFERENT"),
          ),
        ),
      )
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(dpsPrisoner())

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO))).isNotNull()
    }

    @Test
    fun `will report mismatch where Nomis and mapping counts dont match`() = runTest {
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        PrisonerCaseNotesResponse(
          listOf(
            templateNomisCaseNote(1, 1),
            templateNomisCaseNote(2, 2),
            templateNomisCaseNote(3, 3),
            templateNomisCaseNote(4, 3),
          ),
        ),
      )
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(dpsPrisoner())

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO)))
        .isEqualTo(
          MismatchCaseNote(
            offenderNo = "A3456GH",
            diffsForNomis = setOf(4),
            notes = listOf("mappings.size = 3, mappingsDpsDistinctIds.size = 1, nomisCaseNotes.size = 4, dpsCaseNotes.size = 1"),
          ),
        )

      verify(telemetryClient).trackEvent(
        "casenotes-reports-reconciliation-mismatch-size-nomis",
        mapOf(
          "offenderNo" to "A3456GH",
          "message" to "mappings.size = 3, mappingsDpsDistinctIds.size = 1, nomisCaseNotes.size = 4, dpsCaseNotes.size = 1",
          "differences" to "4",
        ),
        null,
      )
    }

    @Test
    fun `will report mismatch where DPS and mapping counts dont match`() = runTest {
      whenever(caseNotesNomisApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        PrisonerCaseNotesResponse(
          listOf(
            templateNomisCaseNote(1, 1),
            templateNomisCaseNote(2, 2),
            templateNomisCaseNote(3, 3),
          ),
        ),
      )
      whenever(caseNotesApiService.getCaseNotesForPrisoner(OFFENDER_NO)).thenReturn(
        listOf(
          templateDpsCaseNote(DPS_CASE_NOTE_ID, OFFENDER_NO),
          templateDpsCaseNote("12345678-0000-0000-0000-000011112345", OFFENDER_NO),
        ),
      )

      assertThat(caseNotesReconciliationService.checkMatch(PrisonerId(OFFENDER_NO)))
        .isEqualTo(
          MismatchCaseNote(
            offenderNo = "A3456GH",
            diffsForDps = setOf("12345678-0000-0000-0000-000011112345"),
            notes = listOf("mappings.size = 3, mappingsDpsDistinctIds.size = 1, nomisCaseNotes.size = 3, dpsCaseNotes.size = 2"),
          ),
        )

      verify(telemetryClient).trackEvent(
        "casenotes-reports-reconciliation-mismatch-size-dps",
        mapOf(
          "offenderNo" to "A3456GH",
          "message" to "mappings.size = 3, mappingsDpsDistinctIds.size = 1, nomisCaseNotes.size = 3, dpsCaseNotes.size = 2",
          "differences" to "12345678-0000-0000-0000-000011112345",
        ),
        null,
      )
    }
  }

  @Nested
  inner class CheckForNomisDuplicateAndDelete {
    @Test
    fun `will delete a duplicate case note in Nomis`() = runTest {
      val mappings = listOf(
        templateMapping(1, "UUID01", OFFENDER_NO),
        templateMapping(2, "UUID02", OFFENDER_NO),
      )
      val nomisCaseNotes = mapOf(
        1L to templateComparison("1", "Other", 1),
        2L to templateComparison("2", "The text - this is duplicated", 2),
        3L to templateComparison("3", "The text - this is duplicated", 3),
      )
      val dpsCaseNotes = mapOf(
        "UUID01" to templateComparison("UUID01", "Other"),
        "UUID02" to templateComparison("UUID02", "The text - this is duplicated"),
      )

      caseNotesReconciliationService.checkForNomisDuplicateAndDelete(
        OFFENDER_NO,
        mappings,
        nomisCaseNotes,
        dpsCaseNotes,
      )

      verify(caseNotesNomisApiService).deleteCaseNote(3L)
      verifyNoMoreInteractions(caseNotesNomisApiService)

      verify(telemetryClient).trackEvent(
        "casenotes-reports-reconciliation-mismatch-deleted",
        mapOf("offenderNo" to "A3456GH", "nomisId" to "3"),
        null,
      )
    }

    @Test
    fun `will delete a duplicate case note in Nomis 2`() = runTest {
      val mappings = listOf(
        templateMapping(1, "UUID01", OFFENDER_NO),
        templateMapping(3, "UUID03", OFFENDER_NO),
      )
      val nomisCaseNotes = mapOf(
        1L to templateComparison("1", "Other", 1),
        2L to templateComparison("2", "The text - this is duplicated", 2),
        3L to templateComparison("3", "The text - this is duplicated", 3),
      )
      val dpsCaseNotes = mapOf(
        "UUID01" to templateComparison("UUID01", "Other", 1),
        "UUID03" to templateComparison("UUID03", "The text - this is duplicated", 3),
      )

      caseNotesReconciliationService.checkForNomisDuplicateAndDelete(
        OFFENDER_NO,
        mappings,
        nomisCaseNotes,
        dpsCaseNotes,
      )

      verify(caseNotesNomisApiService).deleteCaseNote(2L)
      verifyNoMoreInteractions(caseNotesNomisApiService)

      verify(telemetryClient).trackEvent(
        "casenotes-reports-reconciliation-mismatch-deleted",
        mapOf("offenderNo" to "A3456GH", "nomisId" to "2"),
        null,
      )
    }

    @Test
    fun `will NOT delete a duplicate if it is in DPS`() = runTest {
      val mappings = listOf(
        templateMapping(1, "UUID01", OFFENDER_NO),
        templateMapping(2, "UUID02", OFFENDER_NO),
      )
      val nomisCaseNotes = mapOf(
        1L to templateComparison("1", "Other", 1),
        2L to templateComparison("2", "The text - this is duplicated", 2),
        3L to templateComparison("3", "The text - this is duplicated", 3),
      )
      val dpsCaseNotes = mapOf(
        "UUID01" to templateComparison("UUID01", "Other", 1),
        "UUID02" to templateComparison("UUID02", "The text - this is duplicated", 2),
        "UUID03" to templateComparison("UUID03", "The text - this is duplicated", 3),
      )

      caseNotesReconciliationService.checkForNomisDuplicateAndDelete(
        OFFENDER_NO,
        mappings,
        nomisCaseNotes,
        dpsCaseNotes,
      )

      verifyNoInteractions(caseNotesNomisApiService)
    }

    @Test
    fun `will NOT delete a duplicate if neither are in DPS`() = runTest {
      val mappings = listOf(
        templateMapping(1, "UUID01", OFFENDER_NO),
        templateMapping(2, "UUID02", OFFENDER_NO),
      )
      val nomisCaseNotes = mapOf(
        1L to templateComparison("1", "Other", 1),
        2L to templateComparison("2", "The text - this is duplicated", 2),
        3L to templateComparison("3", "The text - this is duplicated", 3),
      )
      val dpsCaseNotes = mapOf(
        "UUID01" to templateComparison("UUID01", "Other", 1),
      )

      caseNotesReconciliationService.checkForNomisDuplicateAndDelete(
        OFFENDER_NO,
        mappings,
        nomisCaseNotes,
        dpsCaseNotes,
      )

      verifyNoInteractions(caseNotesNomisApiService)
    }

    @Test
    fun `will NOT delete a duplicate if neither are in the mapping table`() = runTest {
      val mappings = listOf(
        templateMapping(1, "UUID01", OFFENDER_NO),
        templateMapping(4, "UUID04", OFFENDER_NO),
      )
      val nomisCaseNotes = mapOf(
        1L to templateComparison("1", "Other", 1),
        2L to templateComparison("2", "The text - this is duplicated", 2),
        3L to templateComparison("3", "The text - this is duplicated", 3),
      )
      val dpsCaseNotes = mapOf(
        "UUID01" to templateComparison("UUID01", "Other", 1),
        "UUID02" to templateComparison("UUID02", "The text - this is duplicated", 2),
      )

      caseNotesReconciliationService.checkForNomisDuplicateAndDelete(
        OFFENDER_NO,
        mappings,
        nomisCaseNotes,
        dpsCaseNotes,
      )

      verifyNoInteractions(caseNotesNomisApiService)
    }

    @Test
    fun `will NOT delete a duplicate if it is in the mapping table`() = runTest {
      val mappings = listOf(
        templateMapping(1, "UUID01", OFFENDER_NO),
        templateMapping(2, "UUID02", OFFENDER_NO),
        templateMapping(3, "UUID03", OFFENDER_NO),
      )
      val nomisCaseNotes = mapOf(
        1L to templateComparison("1", "Other", 1),
        2L to templateComparison("2", "The text - this is duplicated", 2),
        3L to templateComparison("3", "The text - this is duplicated", 3),
      )
      val dpsCaseNotes = mapOf(
        "UUID01" to templateComparison("UUID01", "Other", 1),
        "UUID02" to templateComparison("UUID02", "The text - this is duplicated", 2),
      )

      caseNotesReconciliationService.checkForNomisDuplicateAndDelete(
        OFFENDER_NO,
        mappings,
        nomisCaseNotes,
        dpsCaseNotes,
      )

      verifyNoInteractions(caseNotesNomisApiService)
    }

    @Test
    fun `will NOT delete a duplicate if details differ`() = runTest {
      val mappings = listOf(
        templateMapping(1, "UUID01", OFFENDER_NO),
        templateMapping(2, "UUID02", OFFENDER_NO),
      )
      val nomisCaseNotes = mapOf(
        1L to templateComparison("1", "Other", 1),
        2L to templateComparison("2", "The text - this is duplicated", 2),
        3L to templateComparison("3", "The text - this is NOT duplicated", 3),
      )
      val dpsCaseNotes = mapOf(
        "UUID01" to templateComparison("UUID01", "Other", 1),
        "UUID02" to templateComparison("UUID02", "The text - this is duplicated", 2),
      )

      caseNotesReconciliationService.checkForNomisDuplicateAndDelete(
        OFFENDER_NO,
        mappings,
        nomisCaseNotes,
        dpsCaseNotes,
      )

      verifyNoInteractions(caseNotesNomisApiService)
    }

    @Test
    fun `will NOT delete a duplicate if more than 2 are the same`() = runTest {
      val mappings = listOf(
        templateMapping(1, "UUID01", OFFENDER_NO),
        templateMapping(2, "UUID02", OFFENDER_NO),
      )
      val nomisCaseNotes = mapOf(
        1L to templateComparison("1", "Other", 1),
        2L to templateComparison("2", "The text - this is duplicated", 2),
        3L to templateComparison("3", "The text - this is duplicated", 3),
        4L to templateComparison("4", "The text - this is duplicated", 4),
      )
      val dpsCaseNotes = mapOf(
        "UUID01" to templateComparison("UUID01", "Other", 1),
        "UUID02" to templateComparison("UUID02", "The text - this is duplicated", 2),
      )

      caseNotesReconciliationService.checkForNomisDuplicateAndDelete(
        OFFENDER_NO,
        mappings,
        nomisCaseNotes,
        dpsCaseNotes,
      )

      verifyNoInteractions(caseNotesNomisApiService)
    }
  }

  @Nested
  inner class GetLastModified {
    @Test
    fun `will get newest amendment creation date`() = runTest {
      val creationDateTime = parse("2025-01-01T12:00:00")
      val amendment1date = parse("2025-01-02T12:00:00")
      val amendment2date = parse("2025-01-04T12:00:00")
      val amendment3date = parse("2025-01-03T12:00:00")
      val templateAmendment = DpsCaseNoteAmendment(authorUserName = "me", authorName = "Me", additionalNoteText = "text")
      assertThat(
        templateDpsCaseNote(
          dpsId = DPS_CASE_NOTE_ID,
          prisonerNo = OFFENDER_NO,
          creationDateTime = creationDateTime,
          amendments = listOf(
            templateAmendment.copy(creationDateTime = amendment1date),
            templateAmendment.copy(creationDateTime = amendment2date),
            templateAmendment.copy(creationDateTime = amendment3date),
          ),
        ).getLastModified(),
      ).isEqualTo(amendment2date)
    }

    @Test
    fun `will get creation date of unmodified case note`() = runTest {
      val creationDateTime = parse("2025-01-01T12:00:00")
      assertThat(
        templateDpsCaseNote(
          dpsId = DPS_CASE_NOTE_ID,
          prisonerNo = OFFENDER_NO,
          creationDateTime = creationDateTime,
          amendments = listOf(),
        ).getLastModified(),
      ).isEqualTo(creationDateTime)
    }
  }
}

private fun templateComparison(id: String, text: String, legacyId: Long = -1) = ComparisonCaseNote(
  id = id,
  text = text,
  type = "TYPE",
  subType = "SUBTYPE",
  occurrenceDateTime = "2025-01-01T12:00:00",
  creationDateTime = null,
  legacyId = legacyId,
)

private fun templateDpsCaseNote(
  dpsId: String,
  prisonerNo: String,
  type: String = "CODE",
  subType: String = "SUBCODE",
  creationDateTime: LocalDateTime = parse("2024-02-02T01:02:03.567"),
  text: String = "the actual text",
  amendmentText: String = "the amendment text",
  amendments: List<DpsCaseNoteAmendment> = listOf(
    DpsCaseNoteAmendment(
      additionalNoteText = amendmentText,
      authorUserName = "AMUSER",
      authorName = "notused",
      creationDateTime = parse("2024-01-01T01:02:03"),
    ),
  ),
): CaseNote = CaseNote(
  caseNoteId = dpsId,
  text = text,
  type = type,
  subType = subType,
  offenderIdentifier = prisonerNo,
  typeDescription = "notused",
  subTypeDescription = "notused",
  source = "notused",
  creationDateTime = creationDateTime,
  occurrenceDateTime = parse("2024-01-01T01:02:03.678"),
  authorName = "notused",
  authorUserId = "notused",
  authorUsername = "USER",
  eventId = 1234L,
  sensitive = false,
  amendments = amendments,
  systemGenerated = false,
  legacyId = -1L,
)

private fun templateMapping(nomisId: Long, dpsId: String, prisonerNo: String, bookingId: Long = 1) = CaseNoteMappingDto(
  dpsCaseNoteId = dpsId,
  nomisCaseNoteId = nomisId,
  offenderNo = prisonerNo,
  nomisBookingId = bookingId,
  mappingType = MappingType.NOMIS_CREATED,
)

private fun templateNomisCaseNote(
  caseNoteId: Long = 1,
  bookingId: Long = 1,
  type: String = "CODE",
  subType: String = "SUBCODE",
  authorUsername: String = "USER",
  authorUsernames: List<String>? = listOf("USER"),
  creationDateTime: LocalDateTime = parse("2024-02-02T01:02:03"),
  text: String = "the actual text",
  amendmentText: String = "the amendment text",
  amendments: List<CaseNoteAmendment> = listOf(
    CaseNoteAmendment(
      text = amendmentText,
      authorUsername = "AMUSER",
      createdDateTime = parse("2024-01-01T01:02:03"),
      sourceSystem = CaseNoteAmendment.SourceSystem.NOMIS,
    ),
  ),
): CaseNoteResponse = CaseNoteResponse(
  caseNoteId = caseNoteId,
  bookingId = bookingId,
  caseNoteType = CodeDescription(type, "desc"),
  caseNoteSubType = CodeDescription(subType, "desc"),
  authorStaffId = 101L,
  authorUsername = authorUsername,
  authorUsernames = authorUsernames,
  authorLastName = "SMITH",
  caseNoteText = text,
  amendments = amendments,
  occurrenceDateTime = parse("2024-01-01T01:02:03"),
  creationDateTime = creationDateTime,
  createdDatetime = parse("2022-02-02T01:02:03"),
  createdUsername = "notused",
  sourceSystem = CaseNoteResponse.SourceSystem.NOMIS,
)
