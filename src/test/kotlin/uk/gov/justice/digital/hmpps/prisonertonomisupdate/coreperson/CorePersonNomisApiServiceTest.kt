package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePersonReligion
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(
  CorePersonNomisApiService::class,
  CorePersonNomisApiMockServer::class,
  RetryApiService::class,
)
class CorePersonNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: CorePersonNomisApiService

  @Autowired
  private lateinit var mockServer: CorePersonNomisApiMockServer

  @Nested
  inner class GetCorePersonReligion {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetCorePersonReligion("A1234BC")

      apiService.getPrisonerReligion(prisonNumber = "A1234BC")

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetCorePersonReligion("A1234BC")

      apiService.getPrisonerReligion(prisonNumber = "A1234BC")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/core-person/A1234BC/religion")),
      )
    }

    @Test
    fun `will return core person`() = runTest {
      mockServer.stubGetCorePersonReligion(
        prisonNumber = "A1234BC",
        CorePersonReligion(
          prisonNumber = "A1234BC",
          religion = CodeDescription("JEHV", "Jehovah's Witnesses"),
          beliefs = listOf(),
        ),
      )

      val corePerson = apiService.getPrisonerReligion(prisonNumber = "A1234BC")!!

      assertThat(corePerson.religion?.code).isEqualTo("JEHV")
    }
  }
}
