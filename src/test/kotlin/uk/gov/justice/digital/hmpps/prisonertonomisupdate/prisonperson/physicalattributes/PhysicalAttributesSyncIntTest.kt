package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.physicalattributes

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

class PhysicalAttributesSyncIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var nomisApi: PhysicalAttributesNomisApiMockServer

  @Autowired
  private lateinit var dpsApi: PhysicalAttributesDpsApiMockServer

  @Nested
  inner class UpdatePhysicalAttributesEvent {
    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() = runTest {
        dpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA")
        nomisApi.stubPutPhysicalAttributes(offenderNo = "A1234AA")

        awsSnsClient.publish(physicalAttributesRequest()).get()
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should update physical attributes`() {
        // should call DPS API
        dpsApi.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")),
        )

        // should call NOMIS API`
        nomisApi.verify(
          putRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes"))
            .withRequestBody(matchingJsonPath("$.height", equalTo("180")))
            .withRequestBody(matchingJsonPath("$.weight", equalTo("80"))),
        )

        // should publish telemetry
        verify(telemetryClient).trackEvent(
          eq("physical-attributes-update-success"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A1234AA",
                "bookingId" to "12345",
                "created" to "true",
              ),
            )
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class Failures {
      @Test
      fun `should ignore if source system is not DPS`() {
        dpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA")
        nomisApi.stubPutPhysicalAttributes(offenderNo = "A1234AA")

        awsSnsClient.publish(physicalAttributesRequest(source = "NOMIS")).get()
          .also { waitForAnyProcessingToComplete() }

        // should never call out
        dpsApi.verify(0, getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")))
        nomisApi.verify(0, putRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")))
        // should publish ignore event
        verify(telemetryClient).trackEvent(
          eq("physical-attributes-update-ignored"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(mapOf("offenderNo" to "A1234AA"))
          },
          isNull(),
        )
      }

      @Test
      fun `should fail if call to DPS fails`() = runTest {
        dpsApi.stubGetPhysicalAttributes(HttpStatus.BAD_REQUEST)

        awsSnsClient.publish(physicalAttributesRequest()).get()
          .also {
            // event should end up on the DLQ
            await untilCallTo {
              prisonPersonDlqClient!!.countAllMessagesOnQueue(prisonPersonDlqUrl!!).get()
            } matches { it == 1 }
          }

        // should call DPS for each retry
        dpsApi.verify(3, getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")))
        // should never call NOMIS API
        nomisApi.verify(0, putRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")))
        // should publish failed telemetry for each retry
        verify(telemetryClient, times(3)).trackEvent(
          eq("physical-attributes-update-failed"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A1234AA",
                "reason" to "400 Bad Request from GET http://localhost:8097/prisoners/A1234AA/physical-attributes",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `should fail if call to NOMIS fails`() = runTest {
        dpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA")
        nomisApi.stubPutPhysicalAttributes(HttpStatus.BAD_GATEWAY)

        awsSnsClient.publish(physicalAttributesRequest()).get()
          .also {
            // event should end up on the DLQ
            await untilCallTo {
              prisonPersonDlqClient!!.countAllMessagesOnQueue(prisonPersonDlqUrl!!).get()
            } matches { it == 1 }
          }

        // should call DPS and NOMIS APIs for each retry
        dpsApi.verify(3, getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")))
        nomisApi.verify(3, putRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")))
        // should publish failed telemetry for each retry
        verify(telemetryClient, times(3)).trackEvent(
          eq("physical-attributes-update-failed"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A1234AA",
                "reason" to "502 Bad Gateway from PUT http://localhost:8082/prisoners/A1234AA/physical-attributes",
              ),
            )
          },
          isNull(),
        )
      }
    }
  }

  private fun physicalAttributesRequest(prisonerNumber: String = "A1234AA", source: String = "DPS") =
    PublishRequest.builder().topicArn(topicArn)
      .message(physicalAttributesMessage(prisonerNumber, source))
      .messageAttributes(
        mapOf(
          "eventType" to MessageAttributeValue.builder().dataType("String")
            .stringValue("prison-person.physical-attributes.updated").build(),
        ),
      ).build()

  private fun physicalAttributesMessage(prisonerNumber: String, source: String) = """
    {
      "eventType":"prison-person.physical-attributes.updated",
      "additionalInformation": {
        "url":"https://prison-person-api-dev.prison.service.justice.gov.uk/prisoners/$prisonerNumber",
        "source":"$source",
        "prisonerNumber":"$prisonerNumber"
      },
      "occurredAt":"2024-08-12T15:16:44.356649821+01:00",
      "description":"The physical attributes of a prisoner have been updated.",
      "version":"1.0"
    }
  """.trimIndent()

  @Nested
  inner class UpdatePhysicalAttributesApi {

    @Test
    fun `should update physical attributes`() {
      dpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA")
      nomisApi.stubPutPhysicalAttributes(offenderNo = "A1234AA")

      webTestClient.put().uri("/prisonperson/A1234AA/physical-attributes")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_PRISON_PERSON")))
        .exchange()
        .expectStatus().isOk

      // should call DPS API
      dpsApi.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")),
      )

      // should call NOMIS API`
      nomisApi.verify(
        putRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes"))
          .withRequestBody(matchingJsonPath("$.height", equalTo("180")))
          .withRequestBody(matchingJsonPath("$.weight", equalTo("80"))),
      )

      // should publish telemetry
      verify(telemetryClient).trackEvent(
        eq("physical-attributes-update-requested"),
        check {
          assertThat(it).containsExactlyEntriesOf(mapOf("offenderNo" to "A1234AA"))
        },
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("physical-attributes-update-success"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "offenderNo" to "A1234AA",
              "bookingId" to "12345",
              "created" to "true",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should fail on error`() = runTest {
      dpsApi.stubGetPhysicalAttributes(HttpStatus.BAD_REQUEST)

      webTestClient.put().uri("/prisonperson/A1234AA/physical-attributes")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_PRISON_PERSON")))
        .exchange()
        .expectStatus().is5xxServerError

      // should call DPS
      dpsApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")))
      // should never call NOMIS API
      nomisApi.verify(0, putRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")))
      // should publish failed telemetry
      verify(telemetryClient).trackEvent(
        eq("physical-attributes-update-requested"),
        check {
          assertThat(it).containsExactlyEntriesOf(mapOf("offenderNo" to "A1234AA"))
        },
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("physical-attributes-update-failed"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "offenderNo" to "A1234AA",
              "reason" to "400 Bad Request from GET http://localhost:8097/prisoners/A1234AA/physical-attributes",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `access forbidden when no authority`() {
      webTestClient.put().uri("/prisonperson/A1234AA/physical-attributes")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.put().uri("/prisonperson/A1234AA/physical-attributes")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.put().uri("/prisonperson/A1234AA/physical-attributes")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should allow access to DPS reconciliation role`() {
      dpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA")
      nomisApi.stubPutPhysicalAttributes(offenderNo = "A1234AA")

      webTestClient.put().uri("/prisonperson/A1234AA/physical-attributes")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_PRISON_PERSON__RECONCILIATION")))
        .exchange()
        .expectStatus().isOk
    }
  }
}
