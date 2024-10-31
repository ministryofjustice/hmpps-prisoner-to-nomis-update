package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.CSIPDpsApiExtension.Companion.csipDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import java.util.UUID

@SpringAPIServiceTest
@Import(CSIPDpsApiService::class, CSIPConfiguration::class)
class CSIPDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: CSIPDpsApiService

  @Nested
  inner class GetCSIPReport {
    private val dpsCSIPId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      csipDpsApi.stubGetCsipReport()

      apiService.getCsipReport(csipReportId = dpsCSIPId)

      csipDpsApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass csip Id to service`() = runTest {
      csipDpsApi.stubGetCsipReport()

      apiService.getCsipReport(csipReportId = dpsCSIPId)

      csipDpsApi.verify(
        getRequestedFor(urlEqualTo("/csip-records/$dpsCSIPId")),
      )
    }

    @Test
    fun `will return csip`() = runTest {
      csipDpsApi.stubGetCsipReport(
        dpsCsipReport = dpsCsipRecord().copy(
          recordUuid = UUID.fromString(dpsCSIPId),
        ),
      )

      val csip = apiService.getCsipReport(csipReportId = dpsCSIPId)

      assertThat(csip.recordUuid.toString()).isEqualTo(dpsCSIPId)
      assertThat(csip.logCode).isEqualTo("ASI-001")
    }
  }
}
