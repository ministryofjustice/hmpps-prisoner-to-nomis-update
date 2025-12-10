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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderNationality
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.time.LocalDateTime

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
  inner class GetCorePerson {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetCorePerson("A1234BC")

      apiService.getPrisoner(prisonNumber = "A1234BC")

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetCorePerson("A1234BC")

      apiService.getPrisoner(prisonNumber = "A1234BC")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/core-person/A1234BC")),
      )
    }

    @Test
    fun `will return core person`() = runTest {
      mockServer.stubGetCorePerson(
        prisonNumber = "A1234BC",
        CorePerson(
          prisonNumber = "A1234BC",
          activeFlag = true,
          inOutStatus = "IN",
          nationalities = listOf(
            OffenderNationality(
              nationality = CodeDescription("BR", "British"),
              startDateTime = LocalDateTime.now(),
              bookingId = 12345,
              latestBooking = true,
            ),
          ),
        ),
      )

      val corePerson = apiService.getPrisoner(prisonNumber = "A1234BC")!!

      assertThat(corePerson.nationalities?.map { it.nationality.code }).isEqualTo(listOf("BR"))
    }
  }
}
