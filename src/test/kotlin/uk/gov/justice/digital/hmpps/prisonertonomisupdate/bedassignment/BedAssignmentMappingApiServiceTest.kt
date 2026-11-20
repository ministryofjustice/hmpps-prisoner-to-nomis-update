package uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(BedAssignmentMappingApiService::class, BedAssignmentMappingApiMockServer::class, RetryApiService::class)
class BedAssignmentMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: BedAssignmentMappingApiService

  @Autowired
  private lateinit var mockServer: BedAssignmentMappingApiMockServer

  @Nested
  inner class Create {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubPostMapping()

      apiService.create(bedAssignmentMapping())

      mockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the create endpoint`() = runTest {
      mockServer.stubPostMapping()

      apiService.create(bedAssignmentMapping())

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/bed-assignments")),
      )
    }
  }

  @Nested
  inner class GetMappingByNomisIdOrNull {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisId(12345, 1)

      apiService.getMappingByNomisIdOrNull(12345, 1)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return mapping when it exists`() = runTest {
      mockServer.stubGetByNomisId(12345, 1)

      val mapping = apiService.getMappingByNomisIdOrNull(12345, 1)

      assertThat(mapping?.dpsBedAssignmentId).isEqualTo("de91dfa7-821f-4552-a427-bf2f32eafeb0")
      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/bed-assignments/booking-id/12345/sequence/1")),
      )
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisId(HttpStatus.NOT_FOUND)

      assertThat(apiService.getMappingByNomisIdOrNull(12345, 1)).isNull()
    }
  }

  @Nested
  inner class GetMappingByDpsIdOrNull {
    private val dpsId = "de91dfa7-821f-4552-a427-bf2f32eafeb0"

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByDpsId(dpsId)

      apiService.getMappingByDpsIdOrNull(dpsId)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return mapping when it exists`() = runTest {
      mockServer.stubGetByDpsId(dpsId)

      val mapping = apiService.getMappingByDpsIdOrNull(dpsId)

      assertThat(mapping?.nomisBookingId).isEqualTo(12345)
      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/bed-assignments/dps-bed-assignment-id/$dpsId")),
      )
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByDpsId(HttpStatus.NOT_FOUND)

      assertThat(apiService.getMappingByDpsIdOrNull(dpsId)).isNull()
    }
  }

  @Nested
  inner class DeleteByDpsId {
    @Test
    fun `will call the delete endpoint`() = runTest {
      mockServer.stubDeleteByDpsId("1234")

      apiService.deleteByDpsId("1234")

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/bed-assignments/dps-bed-assignment-id/1234"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }
  }
}
