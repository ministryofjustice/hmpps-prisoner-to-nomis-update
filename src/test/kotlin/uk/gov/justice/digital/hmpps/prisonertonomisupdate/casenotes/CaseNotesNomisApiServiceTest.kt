package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.withRequestBodyJsonPath
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AmendCaseNoteRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateCaseNoteRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateCaseNoteResponse

@SpringAPIServiceTest
@Import(CaseNotesNomisApiService::class, CaseNotesNomisApiMockServer::class)
class CaseNotesNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: CaseNotesNomisApiService

  @Autowired
  private lateinit var caseNotesNomisApiMockServer: CaseNotesNomisApiMockServer

  @Nested
  inner class GetCaseNote {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      caseNotesNomisApiMockServer.stubGetCaseNote(caseNoteId = 123)

      apiService.getCaseNote(123)

      caseNotesNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offenderNo to service`() = runTest {
      caseNotesNomisApiMockServer.stubGetCaseNote(caseNoteId = 123)

      apiService.getCaseNote(123)

      caseNotesNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/casenotes/123")),
      )
    }
  }

  @Nested
  inner class CreateCaseNote {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      caseNotesNomisApiMockServer.stubPostCaseNote(offenderNo = "A1234KT")

      apiService.createCaseNote(offenderNo = "A1234KT", nomisCaseNote = createCaseNoteRequest())

      caseNotesNomisApiMockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number to service`() = runTest {
      caseNotesNomisApiMockServer.stubPostCaseNote(offenderNo = "A1234KT")

      apiService.createCaseNote(offenderNo = "A1234KT", nomisCaseNote = createCaseNoteRequest())

      caseNotesNomisApiMockServer.verify(
        postRequestedFor(urlPathEqualTo("/prisoners/A1234KT/casenotes")),
      )
    }

    @Test
    fun `will return NOMIS casenote id`() = runTest {
      caseNotesNomisApiMockServer.stubPostCaseNote(
        offenderNo = "A1234KT",
        caseNote = CreateCaseNoteResponse(
          id = 3,
          bookingId = 123,
        ),
      )

      val nomisCaseNote = apiService.createCaseNote(offenderNo = "A1234KT", nomisCaseNote = createCaseNoteRequest())

      assertThat(nomisCaseNote.id).isEqualTo(3)
      assertThat(nomisCaseNote.bookingId).isEqualTo(123)
    }

    private fun createCaseNoteRequest() = CreateCaseNoteRequest(
      occurrenceDateTime = "2024-07-01",
      caseNoteType = "Security",
      caseNoteSubType = "Security",
      authorUsername = "me",
      caseNoteText = "contents",
    )
  }

  @Nested
  inner class AmendCaseNote {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      caseNotesNomisApiMockServer.stubPutCaseNote(caseNoteId = 4)

      apiService.amendCaseNote(caseNoteId = 4, nomisCaseNote = amendCaseNoteRequest())

      caseNotesNomisApiMockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass casenote Id to service`() = runTest {
      caseNotesNomisApiMockServer.stubPutCaseNote(caseNoteId = 4)

      apiService.amendCaseNote(caseNoteId = 4, nomisCaseNote = amendCaseNoteRequest())

      caseNotesNomisApiMockServer.verify(
        putRequestedFor(urlPathEqualTo("/casenotes/4")),
      )
    }

    @Test
    internal fun `will pass casenote amend request to service`() = runTest {
      caseNotesNomisApiMockServer.stubPutCaseNote(caseNoteId = 4)

      apiService.amendCaseNote(
        caseNoteId = 4,
        nomisCaseNote = AmendCaseNoteRequest(
          occurrenceDateTime = "2024-07-01",
          caseNoteType = "Security",
          caseNoteSubType = "Security",
          authorUsername = "me",
          caseNoteText = "contents",
        ),
      )

      caseNotesNomisApiMockServer.verify(
        putRequestedFor(anyUrl())
          .withRequestBodyJsonPath("occurrenceDateTime", "2024-07-01")
          .withRequestBodyJsonPath("caseNoteType", "Security")
          .withRequestBodyJsonPath("caseNoteSubType", "Security")
          .withRequestBodyJsonPath("authorUsername", "me")
          .withRequestBodyJsonPath("caseNoteText", "contents"),
      )
    }

    private fun amendCaseNoteRequest() = AmendCaseNoteRequest(
      occurrenceDateTime = "2024-07-01",
      caseNoteType = "Security",
      caseNoteSubType = "Security",
      authorUsername = "me",
      caseNoteText = "contents",
    )
  }

  @Nested
  inner class DeleteCaseNote {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      caseNotesNomisApiMockServer.stubDeleteCaseNote(caseNoteId = 4)

      apiService.deleteCaseNote(caseNoteId = 4)

      caseNotesNomisApiMockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass casenote Id to service`() = runTest {
      caseNotesNomisApiMockServer.stubDeleteCaseNote(caseNoteId = 4)

      apiService.deleteCaseNote(caseNoteId = 4)

      caseNotesNomisApiMockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/casenotes/4")),
      )
    }
  }

  @Nested
  inner class GetCaseNotesForPrisoner {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner(offenderNo = "A1234KT")

      apiService.getCaseNotesForPrisoner("A1234KT")

      caseNotesNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offenderNo to service`() = runTest {
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner(offenderNo = "A1234KT")

      apiService.getCaseNotesForPrisoner("A1234KT")

      caseNotesNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234KT/casenotes")),
      )
    }
  }
}
