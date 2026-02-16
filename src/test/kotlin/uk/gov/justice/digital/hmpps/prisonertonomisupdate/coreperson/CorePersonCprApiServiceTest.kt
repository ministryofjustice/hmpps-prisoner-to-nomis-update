package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonCprApiExtension.Companion.corePersonCprApi
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
    internal fun `will pass oath2 token to core person endpoint`() = runTest {
      corePersonCprApi.stubGetCorePerson()

      apiService.getCorePerson("A1234BC")

      corePersonCprApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the GET core person endpoint`() = runTest {
      corePersonCprApi.stubGetCorePerson()

      apiService.getCorePerson("A1234BC")

      corePersonCprApi.verify(
        getRequestedFor(urlPathEqualTo("/person/prison/A1234BC")),
      )
    }
  }
}
