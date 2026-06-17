package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(StaffNomisApiService::class, StaffConfiguration::class, StaffNomisApiMockServer::class, RetryApiService::class)
class StaffNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: StaffNomisApiService

  @Autowired
  private lateinit var mockServer: StaffNomisApiMockServer

  @Nested
  inner class GetStaffIds {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      mockServer.stubGetStaffIds(
        pageNumber = 0,
        pageSize = 20,
        content = listOf(
          StaffIdResponse(
            staffId = 1234,
          ),
        ),
      )

      apiService.getStaffIds(pageNumber = 0, pageSize = 20)

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get IDs endpoint`() = runTest {
      mockServer.stubGetStaffIds(
        pageNumber = 10,
        pageSize = 30,
        content = listOf(
          StaffIdResponse(
            staffId = 1234,
          ),
        ),
      )

      apiService.getStaffIds(pageNumber = 10, pageSize = 30)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/staff/ids"))
          .withQueryParam("page", equalTo("10"))
          .withQueryParam("size", equalTo("30")),
      )
    }
  }

  @Nested
  inner class GetStaffDetails {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      mockServer.stubGetStaff()

      apiService.getStaffDetails(staffId = 1234)

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get visit endpoint`() = runTest {
      mockServer.stubGetStaff()

      apiService.getStaffDetails(staffId = 1234)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/staff/1234")),
      )
    }
  }

  @Nested
  inner class GetStaffIdsFromId {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      mockServer.stubGetStaffIdsFromId(content = listOf(StaffIdResponse(staffId = 1234)))

      apiService.getStaffIdsFromId(lastStaffId = 0, pageSize = 20)

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get IDs endpoint`() = runTest {
      mockServer.stubGetStaffIdsFromId(staffId = 99, content = listOf(StaffIdResponse(staffId = 1234)))

      apiService.getStaffIdsFromId(lastStaffId = 99, pageSize = 30)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/staff/ids/all-from-id"))
          .withQueryParam("staffId", equalTo("99"))
          .withQueryParam("size", equalTo("30")),
      )
    }
  }
}
