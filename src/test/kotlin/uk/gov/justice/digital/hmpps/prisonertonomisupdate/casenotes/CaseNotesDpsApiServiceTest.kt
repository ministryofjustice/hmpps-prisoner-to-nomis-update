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
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A1234TK", caseNotesDpsPagedResponse())

      apiService.getCaseNotesForPrisoner("A1234TK", 0, 10)

      caseNotesDpsApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass offenderNo to service`() = runTest {
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A1234TK", caseNotesDpsPagedResponse())

      apiService.getCaseNotesForPrisoner("A1234TK", 0, 10)

      caseNotesDpsApi.verify(
        getRequestedFor(urlPathEqualTo("/case-notes/A1234TK")),
      )
    }

    @Test
    fun `will return caseNotes`() = runTest {
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A1234TK", caseNotesDpsPagedResponse(300, 300, 400, 0))

      val caseNotes = apiService.getCaseNotesForPrisoner("A1234TK", 0, 400)

      assertThat(caseNotes.content).hasSize(300)
    }
  }
}

fun caseNotesDpsPagedResponse(
  totalElements: Int = 10,
  numberOfElements: Int = 10,
  pageSize: Int = 10,
  pageNumber: Int = 0,
): String {
  val content =
    (1..numberOfElements)
      .map { it + (pageNumber * pageSize) }
      .map { caseNoteDpsJson(id = generateUUID(it), it.toLong()) }
      .joinToString { it }
  return pagedResponse(content, pageSize, pageNumber, totalElements, numberOfElements)
}

fun pagedResponse(
  content: String,
  pageSize: Int,
  pageNumber: Int,
  totalElements: Int,
  pageElements: Int,
) =
  """
  {
      "content": [
          $content
      ],
      "pageable": {
          "offset": 0,
          "pageSize": $pageSize,
          "pageNumber": $pageNumber,
          "paged": true,
          "unpaged": false
      },
      "first": true,
      "last": false,
      "totalPages": ${totalElements / pageSize + 1},
      "totalElements": $totalElements,
      "propertySize": $pageSize,
      "number": $pageNumber,
      "numberOfElements": $pageElements,
      "empty": false
  }                
  """.trimIndent()

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
    "authorUserId": "JBLOGGS",
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
