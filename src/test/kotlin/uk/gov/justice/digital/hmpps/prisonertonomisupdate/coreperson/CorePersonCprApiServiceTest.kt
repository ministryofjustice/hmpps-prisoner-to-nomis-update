package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonCprApiExtension.Companion.corePersonCprApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonReligion.ReligionCode
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(
  CorePersonCprApiService::class,
  CorePersonConfiguration::class,
  CorePersonCprApiMockServer::class,
  RetryApiService::class,
)
class CorePersonCprApiServiceTest {
  @Autowired
  private lateinit var apiService: CorePersonCprApiService

  @Nested
  inner class GetCorePerson {
    @Test
    fun `will call the GET core person endpoint`() = runTest {
      corePersonCprApi.stubGetCorePerson()

      apiService.getCorePerson("A1234BC")

      corePersonCprApi.verify(
        getRequestedFor(urlPathEqualTo("/person/prison/dps/A1234BC")),
      )
    }
  }

  @Nested
  inner class GetReligion {
    private val prisonNumber = "A1234BC"
    private val cprReligionId = "80bbf11f-1ccc-4ad3-a9f1-7c529645b0a1"

    @Test
    fun `will call the GET religion endpoint with prisoner and CPR religion ids`() = runTest {
      corePersonCprApi.stubGetReligion(prisonNumber, cprReligionId)

      apiService.getReligion(prisonNumber, cprReligionId)

      corePersonCprApi.verify(
        getRequestedFor(urlPathEqualTo("/syscon-sync/person/$prisonNumber/religion/$cprReligionId")),
      )
    }

    @Test
    fun `will return the religion record`() = runTest {
      corePersonCprApi.stubGetReligion(prisonNumber, cprReligionId)

      val response = apiService.getReligion(prisonNumber, cprReligionId)

      assertThat(response.prisonNumber).isEqualTo(prisonNumber)
      assertThat(response.religion.cprReligionId).isEqualTo(cprReligionId)
      assertThat(response.religion.religionCode).isEqualTo(ReligionCode.JEHV)
      assertThat(response.religion.comments).isEqualTo("Updated religion")
    }
  }
}
