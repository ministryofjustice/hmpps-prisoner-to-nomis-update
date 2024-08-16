package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.PrisonPersonDpsApiExtension.Companion.prisonPersonDpsApi

@SpringAPIServiceTest
@Import(PrisonPersonDpsApiService::class, PrisonPersonConfiguration::class)
class PrisonPersonDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: PrisonPersonDpsApiService

  @Nested
  inner class GetPrisonPerson {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes()

      apiService.getPhysicalAttributes(prisonerNumber = "A1234AA")

      prisonPersonDpsApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass prisoner number to service`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes()

      apiService.getPhysicalAttributes(prisonerNumber = "A1234AA")

      prisonPersonDpsApi.verify(
        getRequestedFor(urlEqualTo("/prisoners/A1234AA")),
      )
    }

    @Test
    fun `will return physical attributes DTO`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes()

      val pa = apiService.getPhysicalAttributes(prisonerNumber = "A1234AA")

      assertThat(pa.height?.value).isEqualTo(180)
      assertThat(pa.weight?.value).isEqualTo(80)
    }
  }
}
