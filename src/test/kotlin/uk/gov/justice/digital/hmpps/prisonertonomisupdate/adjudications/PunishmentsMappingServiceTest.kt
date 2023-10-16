@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.DuplicateMappingException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AdjudicationPunishmentBatchMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AdjudicationPunishmentMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer

@SpringAPIServiceTest
@Import(PunishmentsMappingService::class)
internal class PunishmentsMappingServiceTest {

  @Autowired
  private lateinit var mappingService: PunishmentsMappingService

  @Nested
  @DisplayName("POST /mapping/punishments")
  inner class CreateMapping {
    @BeforeEach
    internal fun setUp() {
      mappingServer.stubCreatePunishments()
    }

    @Test
    fun `should call mapping api with OAuth2 token`() = runTest {
      mappingService.createMapping(newMapping())

      mappingServer.verify(
        postRequestedFor(urlEqualTo("/mapping/punishments"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post data to mapping api`() = runTest {
      mappingService.createMapping(
        newMapping(
          dpsPunishmentId = "123",
          nomisBookingId = 8765644,
          nomisSanctionSequence = 2,
        ),
      )

      mappingServer.verify(
        postRequestedFor(urlEqualTo("/mapping/punishments"))
          .withRequestBody(matchingJsonPath("punishments[0].dpsPunishmentId", equalTo("123")))
          .withRequestBody(matchingJsonPath("punishments[0].nomisBookingId", equalTo("8765644")))
          .withRequestBody(matchingJsonPath("punishments[0].nomisSanctionSequence", equalTo("2"))),
      )
    }

    @Test
    internal fun `when a 409 response is received a DuplicateMappingException exception is thrown`() = runTest {
      mappingServer.stubCreatePunishmentsWithDuplicateError(
        dpsPunishmentId = "123",
        nomisBookingId = 8765644,
        nomisSanctionSequence = 2,
        duplicateDpsPunishmentId = "456",
        duplicateNomisBookingId = 8765644,
        duplicateNomisSanctionSequence = 2,
      )

      assertThrows<DuplicateMappingException> {
        mappingService.createMapping(newMapping())
      }
    }

    @Test
    internal fun `when other error a web exception is thrown`() = runTest {
      mappingServer.stubCreatePunishmentsWithError(500)

      assertThrows<WebClientResponseException.InternalServerError> {
        mappingService.createMapping(newMapping())
      }
    }
  }

  private fun newMapping(
    dpsPunishmentId: String = "1234",
    nomisBookingId: Long = 456L,
    nomisSanctionSequence: Int = 1,
  ) =
    AdjudicationPunishmentBatchMappingDto(
      punishments = listOf(
        AdjudicationPunishmentMappingDto(
          dpsPunishmentId = dpsPunishmentId,
          nomisBookingId = nomisBookingId,
          nomisSanctionSequence = nomisSanctionSequence,
        ),
      ),
    )
}
