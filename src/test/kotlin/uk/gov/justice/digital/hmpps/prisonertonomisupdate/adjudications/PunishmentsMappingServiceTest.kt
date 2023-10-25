@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AdjudicationPunishmentBatchUpdateMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AdjudicationPunishmentMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AdjudicationPunishmentNomisIdDto
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

  @Nested
  @DisplayName("PUT /mapping/punishments")
  inner class UpdateMapping {
    @BeforeEach
    internal fun setUp() {
      mappingServer.stubUpdatePunishments()
    }

    @Test
    fun `should call mapping api with OAuth2 token`() = runTest {
      mappingService.updateMapping(updateMapping())

      mappingServer.verify(
        putRequestedFor(urlEqualTo("/mapping/punishments"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post data to mapping api`() = runTest {
      mappingService.updateMapping(
        updateMapping(
          dpsPunishmentId = "123",
          nomisBookingId = 8765644,
          nomisSanctionSequence = 2,
          removeNomisBookingId = 77440,
          removeNomisSanctionSequence = 1,
        ),
      )

      mappingServer.verify(
        putRequestedFor(urlEqualTo("/mapping/punishments"))
          .withRequestBody(matchingJsonPath("punishmentsToCreate[0].dpsPunishmentId", equalTo("123")))
          .withRequestBody(matchingJsonPath("punishmentsToCreate[0].nomisBookingId", equalTo("8765644")))
          .withRequestBody(matchingJsonPath("punishmentsToCreate[0].nomisSanctionSequence", equalTo("2")))
          .withRequestBody(matchingJsonPath("punishmentsToDelete[0].nomisSanctionSequence", equalTo("1")))
          .withRequestBody(matchingJsonPath("punishmentsToDelete[0].nomisBookingId", equalTo("77440"))),
      )
    }

    @Test
    internal fun `when a 409 response is received a DuplicateMappingException exception is thrown`() = runTest {
      mappingServer.stubUpdatePunishmentsWithDuplicateError(
        dpsPunishmentId = "123",
        nomisBookingId = 8765644,
        nomisSanctionSequence = 2,
        duplicateDpsPunishmentId = "456",
        duplicateNomisBookingId = 8765644,
        duplicateNomisSanctionSequence = 2,
      )

      assertThrows<DuplicateMappingException> {
        mappingService.updateMapping(updateMapping())
      }
    }

    @Test
    internal fun `when other error a web exception is thrown`() = runTest {
      mappingServer.stubUpdatePunishmentsWithError(500)

      assertThrows<WebClientResponseException.InternalServerError> {
        mappingService.updateMapping(updateMapping())
      }
    }
  }

  @Nested
  @DisplayName("GET /mapping/punishments/:dpsPunishmentId")
  inner class GetMapping {
    @BeforeEach
    internal fun setUp() {
      mappingServer.stubGetPunishments(dpsPunishmentId = "12121", nomisBookingId = 654321, nomisSanctionSequence = 6)
    }

    @Test
    fun `should call mapping api with OAuth2 token`() = runTest {
      mappingService.getMapping(dpsPunishmentId = "12121")

      mappingServer.verify(
        getRequestedFor(urlEqualTo("/mapping/punishments/12121"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will get data from mapping api`() = runTest {
      val mapping = mappingService.getMapping(dpsPunishmentId = "12121")!!

      assertThat(mapping.nomisBookingId).isEqualTo(654321)
      assertThat(mapping.nomisSanctionSequence).isEqualTo(6)
    }

    @Test
    internal fun `404 error converted to null`() = runTest {
      mappingServer.stubGetPunishmentsWithError(dpsPunishmentId = "12121", 404)

      assertThat(mappingService.getMapping(dpsPunishmentId = "12121")).isNull()
    }

    @Test
    internal fun `when other error a web exception is thrown`() = runTest {
      mappingServer.stubGetPunishmentsWithError(dpsPunishmentId = "12121", 500)

      assertThrows<WebClientResponseException.InternalServerError> {
        mappingService.getMapping(dpsPunishmentId = "12121")
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

  private fun updateMapping(
    dpsPunishmentId: String = "1234",
    nomisBookingId: Long = 456L,
    nomisSanctionSequence: Int = 1,
    removeNomisBookingId: Long = 556L,
    removeNomisSanctionSequence: Int = 2,
  ) =
    AdjudicationPunishmentBatchUpdateMappingDto(
      punishmentsToCreate = listOf(
        AdjudicationPunishmentMappingDto(
          dpsPunishmentId = dpsPunishmentId,
          nomisBookingId = nomisBookingId,
          nomisSanctionSequence = nomisSanctionSequence,
        ),
      ),
      punishmentsToDelete = listOf(
        AdjudicationPunishmentNomisIdDto(
          nomisBookingId = removeNomisBookingId,
          nomisSanctionSequence = removeNomisSanctionSequence,
        ),
      ),
    )
}
