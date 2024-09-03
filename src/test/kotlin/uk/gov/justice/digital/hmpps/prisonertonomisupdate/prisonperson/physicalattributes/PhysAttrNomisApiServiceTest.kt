package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.physicalattributes

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.PrisonPersonConfiguration

@SpringAPIServiceTest
@Import(PhysAttrNomisApiService::class, PrisonPersonConfiguration::class, PhysAttrNomisApiMockServer::class)
class PhysAttrNomisApiServiceTest {
  @Autowired
  private lateinit var nomisApiService: PhysAttrNomisApiService

  @Autowired
  private lateinit var nomisApi: PhysAttrNomisApiMockServer

  @Nested
  inner class UpsertPhysicalAttributes {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      nomisApi.stubPutPhysicalAttributes(offenderNo = "A1234KT")

      nomisApiService.upsertPhysicalAttributes(offenderNo = "A1234KT", height = 180, weight = 80)

      nomisApi.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number and attributes to service`() = runTest {
      nomisApi.stubPutPhysicalAttributes(offenderNo = "A1234KT")

      nomisApiService.upsertPhysicalAttributes(offenderNo = "A1234KT", height = 180, weight = 80)

      nomisApi.verify(
        putRequestedFor(urlPathEqualTo("/prisoners/A1234KT/physical-attributes"))
          .withRequestBody(matchingJsonPath("$.height", equalTo("180")))
          .withRequestBody(matchingJsonPath("$.weight", equalTo("80"))),
      )
    }

    @Test
    fun `will return created and booking ID`() = runTest {
      nomisApi.stubPutPhysicalAttributes(offenderNo = "A1234KT", created = true, bookingId = 1234567)

      val response = nomisApiService.upsertPhysicalAttributes(offenderNo = "A1234KT", height = 180, weight = 80)

      assertThat(response.bookingId).isEqualTo(1234567)
      assertThat(response.created).isTrue()
    }
  }
}
