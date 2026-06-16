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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.StaffMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(StaffMappingService::class, StaffMappingApiMockServer::class, RetryApiService::class)
class StaffMappingApiServiceTest {

  @Autowired
  private lateinit var apiService: StaffMappingService

  @Autowired
  private lateinit var mockServer: StaffMappingApiMockServer

  @Nested
  inner class GetStaffByNomisIdOrNull {
    val nomisStaffId = 12345L

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetStaffByNomisIdOrNull(
        nomisStaffId = nomisStaffId,
        mapping = StaffMappingDto(
          dpsId = "1234",
          nomisId = nomisStaffId,
          mappingType = StaffMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getStaffByNomisIdOrNull(
        nomisStaffId = nomisStaffId,
      )

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetStaffByNomisIdOrNull(
        nomisStaffId = nomisStaffId,
        mapping = StaffMappingDto(
          dpsId = "1234",
          nomisId = nomisStaffId,
          mappingType = StaffMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getStaffByNomisIdOrNull(
        nomisStaffId = nomisStaffId,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/staff/nomis-id/$nomisStaffId")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetStaffByNomisIdOrNull(
        nomisStaffId = nomisStaffId,
        mapping = StaffMappingDto(
          dpsId = "1234",
          nomisId = nomisStaffId,
          mappingType = StaffMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getStaffByNomisIdOrNull(
        nomisStaffId = nomisStaffId,
      )

      assertThat(mapping?.dpsId).isEqualTo("1234")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetStaffByNomisIdOrNull(
        nomisStaffId = nomisStaffId,
        mapping = null,
      )

      assertThat(
        apiService.getStaffByNomisIdOrNull(
          nomisStaffId = nomisStaffId,
        ),
      ).isNull()
    }
  }
}
