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

@SpringAPIServiceTest
@Import(CaseNotesDpsApiService::class, CaseNotesConfiguration::class, RetryApiService::class)
class CaseNotesDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: CaseNotesDpsApiService

  @Nested
  inner class GetCaseNote {
    private val dpsCaseNoteId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      caseNotesDpsApi.stubGetCaseNote()

      apiService.getCaseNote(caseNoteId = dpsCaseNoteId)

      caseNotesDpsApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass caseNote Id to service`() = runTest {
      caseNotesDpsApi.stubGetCaseNote()

      apiService.getCaseNote(caseNoteId = dpsCaseNoteId)

      caseNotesDpsApi.verify(
        getRequestedFor(urlEqualTo("/case-notes/case-note-id/$dpsCaseNoteId")),
      )
    }

    @Test
    fun `will return caseNote`() = runTest {
      caseNotesDpsApi.stubGetCaseNote(caseNote = dpsCaseNote().copy(caseNoteId = dpsCaseNoteId, authorName = "me"))

      val caseNote = apiService.getCaseNote(dpsCaseNoteId)

      assertThat(caseNote.caseNoteId).isEqualTo(dpsCaseNoteId)
      assertThat(caseNote.authorName).isEqualTo("me")
    }
  }

  @Nested
  inner class GetCaseNotesForPrisoner {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A1234TK")

      apiService.getCaseNotesForPrisoner("A1234TK")

      caseNotesDpsApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offenderNo to service`() = runTest {
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A1234TK")

      apiService.getCaseNotesForPrisoner("A1234TK")

      caseNotesDpsApi.verify(
        getRequestedFor(urlPathEqualTo("/case-notes/A1234TK")),
      )
    }

    @Test
    fun `will return caseNotes`() = runTest {
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A1234TK", count = 300)

      val caseNotes = apiService.getCaseNotesForPrisoner("A1234TK")

      assertThat(caseNotes).hasSize(300)
    }
  }
}
