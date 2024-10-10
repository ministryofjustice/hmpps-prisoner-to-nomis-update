package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.CaseNotesDpsApiExtension.Companion.caseNotesDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

private val dpsCaseNoteId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"
private val offenderNo = "A3456RW"

@SpringAPIServiceTest
@Import(CaseNotesDpsApiService::class, CaseNotesConfiguration::class, RetryApiService::class)
class CaseNotesDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: CaseNotesDpsApiService

  @Nested
  inner class GetCaseNote {

    @Test
    fun `will pass oath2 token to service`() = runTest {
      caseNotesDpsApi.stubGetCaseNote()

      apiService.getCaseNote(offenderNo, dpsCaseNoteId)

      caseNotesDpsApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass caseNote Id to service`() = runTest {
      caseNotesDpsApi.stubGetCaseNote()

      apiService.getCaseNote(offenderNo, dpsCaseNoteId)

      caseNotesDpsApi.verify(
        getRequestedFor(urlEqualTo("/case-notes/$offenderNo/$dpsCaseNoteId")),
      )
    }

    @Test
    fun `will return caseNote`() = runTest {
      caseNotesDpsApi.stubGetCaseNote(caseNote = dpsCaseNote().copy(caseNoteId = dpsCaseNoteId, authorName = "me"))

      val caseNote = apiService.getCaseNote(offenderNo, dpsCaseNoteId)

      assertThat(caseNote.caseNoteId).isEqualTo(dpsCaseNoteId)
      assertThat(caseNote.authorName).isEqualTo("me")
    }
  }

  @Nested
  inner class GetCaseNotesForPrisoner {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A1234TK", caseNotesDpsResponse())

      apiService.getCaseNotesForPrisoner("A1234TK")

      caseNotesDpsApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass offenderNo to service`() = runTest {
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A1234TK", caseNotesDpsResponse())

      apiService.getCaseNotesForPrisoner("A1234TK")

      caseNotesDpsApi.verify(
        getRequestedFor(urlPathEqualTo("/sync/case-notes/A1234TK")),
      )
    }

    @Test
    fun `will return caseNotes`() = runTest {
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A1234TK", caseNotesDpsResponse(300))

      val caseNotes = apiService.getCaseNotesForPrisoner("A1234TK")

      assertThat(caseNotes).hasSize(300)
    }
  }
}

fun caseNotesDpsResponse(
  numberOfElements: Int = 10,
): String {
  val content =
    (1..numberOfElements)
      .map { caseNoteDpsJson(id = generateUUID(it), it.toLong()) }
      .joinToString { it }
  return "[$content]"
}

fun caseNoteDpsJson(id: String, nomisId: Long) =
  """
  {
    "caseNoteId": "$id",
    "offenderIdentifier": "A1234AA",
    "type": "CODE",
    "typeDescription": "type description",
    "subType": "SUBCODE",
    "subTypeDescription": "subtype description",
    "source": "NOMIS",
    "creationDateTime": "2024-05-25T02:03:04",
    "occurrenceDateTime": "2024-05-25T02:03:04",
    "authorName": "Joe Bloggs",
    "authorUserId": "123456",
    "authorUsername": "JBLOGGS",
    "text": "The actual case note",
    "eventId": $nomisId,
    "sensitive": false,
    "amendments": [{
      "authorUserName": "SRENDELL",
      "authorName": "Steve Rendell",
      "additionalNoteText": "The actual amendment"
    }],
    "systemGenerated": false,
    "legacyId": $nomisId
  }
  """.trimIndent()

fun generateUUID(it: Int) = "de91dfa7-821f-4552-a427-111111${it.toString().padStart(6, '0')}"
