package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertCSIPRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertCSIPResponse
import java.time.LocalDate

@SpringAPIServiceTest
@Import(CSIPNomisApiService::class, CSIPConfiguration::class, CSIPNomisApiMockServer::class)
class CSIPNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: CSIPNomisApiService

  @Autowired
  private lateinit var csipNomisApiMockServer: CSIPNomisApiMockServer

  @Nested
  inner class CreateCSIP {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      csipNomisApiMockServer.stubPutCSIP()

      apiService.upsertCsipReport(nomisCSIPReport = createCSIPRequest())

      csipNomisApiMockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number to service`() = runTest {
      csipNomisApiMockServer.stubPutCSIP()

      apiService.upsertCsipReport(nomisCSIPReport = createCSIPRequest())

      csipNomisApiMockServer.verify(
        putRequestedFor(urlPathEqualTo("/csip")),
      )
    }

    @Test
    fun `will return NOMIS CSIP id`() = runTest {
      csipNomisApiMockServer.stubPutCSIP(
        csipResponse = UpsertCSIPResponse(
          nomisCSIPReportId = 1234567,
          offenderNo = "A1234BC",
          created = true,

        ),
      )

      val nomisCSIP = apiService.upsertCsipReport(nomisCSIPReport = createCSIPRequest())

      assertThat(nomisCSIP.nomisCSIPReportId).isEqualTo(1234567)
      assertThat(nomisCSIP.offenderNo).isEqualTo("A1234BC")
      assertThat(nomisCSIP.created).isEqualTo(true)
    }

    private fun createCSIPRequest(): UpsertCSIPRequest = UpsertCSIPRequest(
      offenderNo = "A1234TT",
      incidentDate = LocalDate.parse("2023-12-23"),
      typeCode = "INT",
      locationCode = "LIB",
      areaOfWorkCode = "EDU",
      reportedBy = "Jane Reporter",
      reportedDate = LocalDate.now(),
      staffAssaulted = false,
      proActiveReferral = false,
    )
  }

  @Nested
  inner class DeleteCSIP {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      csipNomisApiMockServer.stubDeleteCSIP(nomisCSIPReportId = 1234567)

      apiService.deleteCsipReport(csipReportId = 1234567)

      csipNomisApiMockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass CSIP Id to service`() = runTest {
      csipNomisApiMockServer.stubDeleteCSIP(nomisCSIPReportId = 1234567)

      apiService.deleteCsipReport(csipReportId = 1234567)

      csipNomisApiMockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/csip/1234567")),
      )
    }
  }
}
