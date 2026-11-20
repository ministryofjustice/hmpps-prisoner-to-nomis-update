package uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(BedAssignmentNomisApiService::class, BedAssignmentNomisApiMockServer::class, RetryApiService::class)
class BedAssignmentNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: BedAssignmentNomisApiService

  @Autowired
  private lateinit var mockServer: BedAssignmentNomisApiMockServer

  @Nested
  inner class GetBedAssignmentsForPrisoner {
    @Test
    fun `will pass oath2 token to endpoint`() = runTest {
      mockServer.stubGetBedAssignmentsForPrisoner("A1234AA")

      apiService.getBedAssignmentsForPrisoner("A1234AA")

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get endpoint and return the assignments`() = runTest {
      mockServer.stubGetBedAssignmentsForPrisoner("A1234AA")

      val response = apiService.getBedAssignmentsForPrisoner("A1234AA")

      assertThat(response.bedAssignments).hasSize(1)
      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/bed-assignments")),
      )
    }
  }

  @Nested
  inner class GetBedAssignmentOrNull {
    @Test
    fun `will pass oath2 token to endpoint`() = runTest {
      mockServer.stubGetBedAssignment(12345, 1)

      apiService.getBedAssignmentOrNull(12345, 1)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get endpoint and return the assignment`() = runTest {
      mockServer.stubGetBedAssignment(12345, 1)

      val response = apiService.getBedAssignmentOrNull(12345, 1)

      assertThat(response?.livingUnitId).isEqualTo(4321)
      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/booking-id/12345/bed-assignments/1")),
      )
    }

    @Test
    fun `will return null when not found`() = runTest {
      mockServer.stubGetBedAssignmentError(12345, 1, 404)

      assertThat(apiService.getBedAssignmentOrNull(12345, 1)).isNull()
    }
  }
}
