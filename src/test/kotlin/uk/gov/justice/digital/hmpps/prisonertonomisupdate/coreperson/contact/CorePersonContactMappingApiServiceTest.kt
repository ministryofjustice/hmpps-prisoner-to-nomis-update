package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.contact

import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CorePersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(CorePersonContactMappingApiService::class, CorePersonContactMappingApiMockServer::class, RetryApiService::class)
class CorePersonContactMappingApiServiceTest(
  @Autowired private val apiService: CorePersonContactMappingApiService,
  @Autowired private val mockServer: CorePersonContactMappingApiMockServer,
  @Autowired private val jsonMapper: JsonMapper,
) {

  @Nested
  inner class GetByCprContactIdOrNull {
    private val cprContactId = "11111111-2222-3333-4444-555555555555"

    @Test
    fun `will request a mapping by CPR contact id`() = runTest {
      mockServer.stubGetContactMapping(cprContactId)

      apiService.getByCprContactIdOrNull(cprContactId)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/core-person/contact/cpr-contact-id/$cprContactId")),
      )
    }

    @Test
    fun `will return the mapping`() = runTest {
      mockServer.stubGetContactMapping(cprContactId)

      val mapping = apiService.getByCprContactIdOrNull(cprContactId)

      assertThat(mapping?.cprId).isEqualTo(cprContactId)
      assertThat(mapping?.nomisId).isEqualTo(12345L)
      assertThat(mapping?.nomisContactType).isEqualTo(CorePersonContactMappingDto.NomisContactType.PHONE)
      assertThat(mapping?.nomisPrisonNumber).isEqualTo("A1234AA")
    }

    @Test
    fun `will return null when the mapping does not exist`() = runTest {
      mockServer.stubGetContactMapping(cprContactId, mapping = null)

      assertThat(apiService.getByCprContactIdOrNull(cprContactId)).isNull()
    }
  }

  @Nested
  inner class CreateContactMapping {
    private val mapping = CorePersonContactMappingDto(
      cprId = "11111111-2222-3333-4444-555555555555",
      nomisId = 12345L,
      nomisContactType = CorePersonContactMappingDto.NomisContactType.EMAIL,
      nomisPrisonNumber = "A1234AA",
      mappingType = CorePersonContactMappingDto.MappingType.CPR_CREATED,
    )

    @Test
    fun `will pass mapping to service`() = runTest {
      mockServer.stubCreateContactMapping()

      apiService.createContactMapping(mapping)

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/core-person/contact"))
          .withRequestBody(equalToJson(jsonMapper.writeValueAsString(mapping))),
      )
    }
  }
}
