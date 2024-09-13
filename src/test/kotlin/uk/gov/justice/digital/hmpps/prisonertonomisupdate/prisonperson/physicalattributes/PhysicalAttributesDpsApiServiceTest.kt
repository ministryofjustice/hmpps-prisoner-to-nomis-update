package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.physicalattributes

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
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.PrisonPersonConfiguration

@SpringAPIServiceTest
@Import(PhysicalAttributesDpsApiService::class, PrisonPersonConfiguration::class, PhysicalAttributesDpsApiMockServer::class)
class PhysicalAttributesDpsApiServiceTest {
  @Autowired
  private lateinit var dpsApiService: PhysicalAttributesDpsApiService

  @Autowired
  private lateinit var dpsApi: PhysicalAttributesDpsApiMockServer

  @Nested
  inner class GetPrisonPerson {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      dpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA")

      dpsApiService.getPhysicalAttributes(prisonerNumber = "A1234AA")

      dpsApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass prisoner number to service`() = runTest {
      dpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA")

      dpsApiService.getPhysicalAttributes(prisonerNumber = "A1234AA")

      dpsApi.verify(
        getRequestedFor(urlEqualTo("/prisoners/A1234AA/physical-attributes")),
      )
    }

    @Test
    fun `will return physical attributes DTO`() = runTest {
      dpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA")

      val pa = dpsApiService.getPhysicalAttributes(prisonerNumber = "A1234AA")

      assertThat(pa?.height?.value).isEqualTo(180)
      assertThat(pa?.weight?.value).isEqualTo(80)
    }

    @Test
    fun `will return null if not found`() = runTest {
      dpsApi.stubGetPhysicalAttributes(NOT_FOUND)

      assertThat(dpsApiService.getPhysicalAttributes(prisonerNumber = "A1234AA")).isNull()
    }
  }
}
