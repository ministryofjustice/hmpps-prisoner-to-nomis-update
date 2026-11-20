package uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment.BedAssignmentDpsApiExtension.Companion.bedAssignmentApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment.model.NomisBedAssignment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(BedAssignmentDpsApiService::class, BedAssignmentConfiguration::class, RetryApiService::class)
class BedAssignmentDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: BedAssignmentDpsApiService

  private val nomisBedAssignment = NomisBedAssignment(
    bookingId = 12345,
    sequence = 1,
    livingUnitId = 4321,
    assignmentDateTime = LocalDateTime.parse("2026-01-02T10:00:00"),
    createdBy = "ME",
  )

  @Nested
  inner class SyncBedAssignments {
    @Test
    fun `will pass oath2 token to endpoint`() = runTest {
      bedAssignmentApi.stubSyncBedAssignments("A1234AA")

      apiService.syncBedAssignments("A1234AA", listOf(nomisBedAssignment))

      bedAssignmentApi.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the sync endpoint`() = runTest {
      bedAssignmentApi.stubSyncBedAssignments("A1234AA")

      apiService.syncBedAssignments("A1234AA", listOf(nomisBedAssignment))

      bedAssignmentApi.verify(
        putRequestedFor(urlPathEqualTo("/nomis-sync/bed-assignments/sync/A1234AA")),
      )
    }
  }

  @Nested
  inner class MigrateBedAssignments {
    @Test
    fun `will pass oath2 token to endpoint`() = runTest {
      bedAssignmentApi.stubMigrateBedAssignments("A1234AA")

      apiService.migrateBedAssignments("A1234AA", listOf(nomisBedAssignment))

      bedAssignmentApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the migrate endpoint`() = runTest {
      bedAssignmentApi.stubMigrateBedAssignments("A1234AA")

      apiService.migrateBedAssignments("A1234AA", listOf(nomisBedAssignment))

      bedAssignmentApi.verify(
        postRequestedFor(urlPathEqualTo("/nomis-sync/bed-assignments/migrate/A1234AA")),
      )
    }
  }

  @Nested
  inner class GetCellMovementReasonOrNull {
    @Test
    fun `will pass oath2 token to endpoint`() = runTest {
      bedAssignmentApi.stubGetCellMovementReason(12345, 1)

      apiService.getCellMovementReasonOrNull(12345, 1)

      bedAssignmentApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get endpoint and return the reason`() = runTest {
      bedAssignmentApi.stubGetCellMovementReason(12345, 1)

      val reason = apiService.getCellMovementReasonOrNull(12345, 1)

      assertThat(reason?.reasonCode).isEqualTo("ADM")
      bedAssignmentApi.verify(
        getRequestedFor(urlPathEqualTo("/cell-movements/12345/bed-assignment/1")),
      )
    }

    @Test
    fun `will return null when not found`() = runTest {
      bedAssignmentApi.stubGetCellMovementReasonError(12345, 1, 404)

      assertThat(apiService.getCellMovementReasonOrNull(12345, 1)).isNull()
    }
  }
}
