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
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.model.CaseNote
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AllPrisonerCaseNoteMappingsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CaseNoteMappingDto.MappingType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CaseNoteAmendment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PrisonerCaseNotesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDateTime.parse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.model.CaseNoteAmendment as DpsCaseNoteAmendment

private const val OFFENDER_NO = "A3456GH"
private const val DPS_CASE_NOTE_ID = "12345678-0000-0000-0000-000011112222"

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
  ) = PrisonerCaseNotesResponse(listOf(templateNomisCaseNote(1, 1, type, subType, authorUsername, authorUsernames)))

  fun dpsPrisoner(
    type: String = "CODE",
    subType: String = "SUBCODE",
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
    templateDpsCaseNote(DPS_CASE_NOTE_ID, OFFENDER_NO, type, subType, amendmentText, amendments),
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
          missingFromDps = emptySet(),
          missingFromNomis = emptySet(),
          notes = listOf(
            "dpsCaseNote = {id=1, text-hash=-622354608, type=OTHER, subType=SUBCODE, occurrenceDateTime=2024-01-01T01:02:03, creationDateTime=2024-02-02T01:02:03, authorUsername=USER, amendments=[{text-hash=1137158639, occurrenceDateTime=2024-01-01T01:02:03, authorUsername=AMUSER}]}," +
              " nomisCaseNote = {id=1, text-hash=-622354608, type=CODE, subType=SUBCODE, occurrenceDateTime=2024-01-01T01:02:03, creationDateTime=2024-02-02T01:02:03, authorUsername=[USER], amendments=[{text-hash=1137158639, occurrenceDateTime=2024-01-01T01:02:03, authorUsername=AMUSER}]}",
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
              "1" to "dpsCaseNote = {id=1, text-hash=-622354608, type=OTHER, subType=SUBCODE, occurrenceDateTime=2024-01-01T01:02:03, creationDateTime=2024-02-02T01:02:03, authorUsername=USER, amendments=[{text-hash=1137158639, occurrenceDateTime=2024-01-01T01:02:03, authorUsername=AMUSER}]}," +
                " nomisCaseNote = {id=1, text-hash=-622354608, type=CODE, subType=SUBCODE, occurrenceDateTime=2024-01-01T01:02:03, creationDateTime=2024-02-02T01:02:03, authorUsername=[USER], amendments=[{text-hash=1137158639, occurrenceDateTime=2024-01-01T01:02:03, authorUsername=AMUSER}]}",
            ),
          )
        },
        isNull(),
      )
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
            missingFromDps = emptySet(),
            missingFromNomis = emptySet(),
            notes = listOf("mappings.size = 3, dpsDistinctIds.size = 1, nomisCaseNotes.size = 4, dpsCaseNotes.size = 1"),
          ),
        )

      verify(telemetryClient).trackEvent(
        "casenotes-reports-reconciliation-mismatch-size-nomis",
        mapOf(
          "offenderNo" to "A3456GH",
          "message" to "mappings.size = 3, dpsDistinctIds.size = 1, nomisCaseNotes.size = 4, dpsCaseNotes.size = 1",
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
            missingFromDps = emptySet(),
            missingFromNomis = emptySet(),
            notes = listOf("mappings.size = 3, dpsDistinctIds.size = 1, nomisCaseNotes.size = 3, dpsCaseNotes.size = 2"),
          ),
        )

      verify(telemetryClient).trackEvent(
        "casenotes-reports-reconciliation-mismatch-size-dps",
        mapOf(
          "offenderNo" to "A3456GH",
          "message" to "mappings.size = 3, dpsDistinctIds.size = 1, nomisCaseNotes.size = 3, dpsCaseNotes.size = 2",
        ),
        null,
      )
    }
  }
}

private fun templateDpsCaseNote(
  dpsId: String,
  prisonerNo: String,
  type: String = "CODE",
  subType: String = "SUBCODE",
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
  text = "the actual text",
  type = type,
  subType = subType,
  offenderIdentifier = prisonerNo,
  typeDescription = "notused",
  subTypeDescription = "notused",
  source = "notused",
  creationDateTime = parse("2024-02-02T01:02:03.567"),
  occurrenceDateTime = parse("2024-01-01T01:02:03.678"),
  authorName = "notused",
  authorUserId = "notused",
  authorUsername = "USER",
  eventId = 1234L,
  sensitive = false,
  amendments = amendments,
  systemGenerated = false,
  legacyId = 1L,
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
): CaseNoteResponse = CaseNoteResponse(
  caseNoteId = caseNoteId,
  bookingId = bookingId,
  caseNoteType = CodeDescription(type, "desc"),
  caseNoteSubType = CodeDescription(subType, "desc"),
  authorStaffId = 101L,
  authorUsername = authorUsername,
  authorUsernames = authorUsernames,
  authorLastName = "SMITH",
  caseNoteText = "the actual text",
  amendments = listOf(
    CaseNoteAmendment(
      text = "the amendment text",
      authorUsername = "AMUSER",
      createdDateTime = "2024-01-01T01:02:03",
      sourceSystem = CaseNoteAmendment.SourceSystem.NOMIS,
    ),
  ),
  occurrenceDateTime = "2024-01-01T01:02:03",
  creationDateTime = "2024-02-02T01:02:03",
  createdDatetime = "notused",
  createdUsername = "notused",
  sourceSystem = CaseNoteResponse.SourceSystem.NOMIS,
)
