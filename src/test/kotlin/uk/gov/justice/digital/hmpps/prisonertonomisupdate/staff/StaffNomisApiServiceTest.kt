package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.IdRange
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

@SpringAPIServiceTest
@Import(StaffNomisApiService::class, StaffConfiguration::class, StaffNomisApiMockServer::class, RetryApiService::class)
class StaffNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: StaffNomisApiService

  @Autowired
  private lateinit var mockServer: StaffNomisApiMockServer

  @Nested
  inner class GetStaffDetails {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      mockServer.stubGetStaffById()

      apiService.getStaffDetails(staffId = 1234)

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get visit endpoint`() = runTest {
      mockServer.stubGetStaffById()

      apiService.getStaffDetails(staffId = 1234)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/staff/id/1234")),
      )
    }
  }

  @Nested
  inner class GetAllStaffIdRanges {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetAllStaffIdRanges(pageSize = 10)

      apiService.getAllStaffIdRanges(
        pageSize = 10,
      )

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass params to service`() = runTest {
      mockServer.stubGetAllStaffIdRanges(pageSize = 5)

      apiService.getAllStaffIdRanges(
        pageSize = 5,
        activeOnly = false,
      )

      nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/staff/id-ranges"))
          .withQueryParam("size", equalTo("5"))
          .withQueryParam("active", equalTo("false")),

      )
    }

    @Test
    fun `will return a range of staff ids`() = runTest {
      mockServer.stubGetAllStaffIdRanges(pageSize = 10, totalElements = 20)

      val staffIds = apiService.getAllStaffIdRanges(pageSize = 10)

      assertThat(staffIds).hasSize(2)
      assertThat(staffIds[0]).isEqualTo(IdRange(0, 10))
      assertThat(staffIds[1]).isEqualTo(IdRange(10, 20))
    }
  }

  @Nested
  inner class GetAllStaffIdsInRange {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetAllStaffIdsInRange(0, 20)

      apiService.getAllStaffIdsInRange(
        fromStaffId = 0,
        toStaffId = 20,
      )

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass params to service`() = runTest {
      mockServer.stubGetAllStaffIdsInRange(5, 100)

      apiService.getAllStaffIdsInRange(
        fromStaffId = 5,
        toStaffId = 100,
        activeOnly = false,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/staff/ids"))
          .withQueryParam("fromId", equalTo("5"))
          .withQueryParam("toId", equalTo("100"))
          .withQueryParam("active", equalTo("false")),
      )
    }

    @Test
    fun `will return a range of staff ids - range is greater than fromStaffId`() = runTest {
      mockServer.stubGetAllStaffIdsInRange(fromStaffId = 0, toStaffId = 10)

      val staffIds = apiService.getAllStaffIdsInRange(
        fromStaffId = 0,
        toStaffId = 10,
      )

      assertThat(staffIds).hasSize(11)
      assertThat(staffIds[0]).isEqualTo(0)
      assertThat(staffIds[1]).isEqualTo(1)
      assertThat(staffIds[10]).isEqualTo(10)
    }
  }
}
