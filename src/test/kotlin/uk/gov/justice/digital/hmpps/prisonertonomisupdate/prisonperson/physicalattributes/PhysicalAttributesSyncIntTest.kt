package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.physicalattributes

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.profiledetails.ProfileDetailsNomisApiMockServer
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

class PhysicalAttributesSyncIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var physicalAttributesNomisApi: PhysicalAttributesNomisApiMockServer

  @Autowired
  private lateinit var profileDetailsNomisApi: ProfileDetailsNomisApiMockServer

  @Autowired
  private lateinit var dpsApi: PhysicalAttributesDpsApiMockServer

  @Nested
  inner class UpdatePhysicalAttributesEvent {
    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() = runTest {
        dpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA")
      }

      @ParameterizedTest
      @CsvSource(
        "null",
        "[HEIGHT]",
        "[WEIGHT]",
        "'[HEIGHT, WEIGHT]'",
        nullValues = ["null"],
      )
      fun `should update height and weight`(fields: String?) {
        physicalAttributesNomisApi.stubPutPhysicalAttributes(offenderNo = "A1234AA")

        awsSnsClient.publish(physicalAttributesRequest(fields = fields)).get()
          .also { waitForAnyProcessingToComplete() }

        verifyDpsApiCall()
        verifyNomisPutPhysicalAttributes(height = equalTo("180"), weight = equalTo("80"))
        verifyTelemetry("physical-attributes-update-success", (fields ?: "null"))
      }

      @ParameterizedTest
      @CsvSource(
        "[BUILD],BUILD,SMALL",
        "[FACE],FACE,ROUND",
        "[FACIAL_HAIR],FACIAL_HAIR,CLEAN_SHAVEN",
        "[HAIR],HAIR,BLACK",
        "[LEFT_EYE_COLOUR],L_EYE_C,BLUE",
        "[RIGHT_EYE_COLOUR],R_EYE_C,GREEN",
        "[SHOE_SIZE],SHOESIZE,9.5",
      )
      fun `should update single profile details`(requestedFields: String, nomisField: String, expectedValue: String) {
        profileDetailsNomisApi.stubPutProfileDetails(offenderNo = "A1234AA")

        awsSnsClient.publish(physicalAttributesRequest(fields = requestedFields)).get()
          .also { waitForAnyProcessingToComplete() }

        verifyDpsApiCall()
        verifyNomisPutProfileDetails(profileType = equalTo(nomisField), profileCode = equalTo(expectedValue))
        verifyTelemetry("profile-details-update-success", requestedFields)
      }

      @Test
      fun `should handle multiple fields updated`() {
        physicalAttributesNomisApi.stubPutPhysicalAttributes(offenderNo = "A1234AA")
        profileDetailsNomisApi.stubPutProfileDetails(offenderNo = "A1234AA")

        awsSnsClient.publish(physicalAttributesRequest(fields = "[HEIGHT, BUILD, SHOE_SIZE]")).get()
          .also { waitForAnyProcessingToComplete(3) }

        verifyDpsApiCall()

        verifyNomisPutPhysicalAttributes(height = equalTo("180"), weight = equalTo("80"))
        verifyNomisPutProfileDetails(profileType = equalTo("BUILD"), profileCode = equalTo("SMALL"))
        verifyNomisPutProfileDetails(profileType = equalTo("SHOESIZE"), profileCode = equalTo("9.5"))

        verifyTelemetry("physical-attributes-update-success", "[HEIGHT]")
        verifyTelemetry("profile-details-update-success", "[BUILD]")
        verifyTelemetry("profile-details-update-success", "[SHOE_SIZE]")
      }

      @Test
      fun `should handle null values`() {
        dpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA", height = null, build = null)
        physicalAttributesNomisApi.stubPutPhysicalAttributes(offenderNo = "A1234AA")
        profileDetailsNomisApi.stubPutProfileDetails(offenderNo = "A1234AA")

        awsSnsClient.publish(physicalAttributesRequest(fields = "[HEIGHT, BUILD]")).get()
          .also { waitForAnyProcessingToComplete(2) }

        verifyDpsApiCall()

        verifyNomisPutPhysicalAttributes(height = absent(), weight = equalTo("80"))
        verifyNomisPutProfileDetails(profileType = equalTo("BUILD"), profileCode = absent())

        verifyTelemetry("physical-attributes-update-success", "[HEIGHT]")
        verifyTelemetry("profile-details-update-success", "[BUILD]")
      }
    }

    @Nested
    inner class Failures {
      @Test
      fun `should ignore if source system is not DPS`() {
        dpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA")
        physicalAttributesNomisApi.stubPutPhysicalAttributes(offenderNo = "A1234AA")

        awsSnsClient.publish(physicalAttributesRequest(source = "NOMIS")).get()
          .also { waitForAnyProcessingToComplete() }

        // should never call out
        dpsApi.verify(0, getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")))
        physicalAttributesNomisApi.verify(0, putRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")))
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
        physicalAttributesNomisApi.verify(0, putRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")))
        // should publish failed telemetry for each retry
        verify(telemetryClient, times(3)).trackEvent(
          eq("physical-attributes-update-error"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A1234AA",
                "reason" to "400 Bad Request from GET http://localhost:8097/prisoners/A1234AA/physical-attributes",
                "fields" to "null",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `should fail if call to DPS returns not found`() = runTest {
        dpsApi.stubGetPhysicalAttributes(HttpStatus.NOT_FOUND)

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
        physicalAttributesNomisApi.verify(0, putRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")))
        // should publish failed telemetry for each retry
        verify(telemetryClient, times(3)).trackEvent(
          eq("physical-attributes-update-error"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A1234AA",
                "reason" to "No physical attributes found for offenderNo: A1234AA",
                "fields" to "null",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `should fail if call to NOMIS fails`() = runTest {
        dpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA")
        physicalAttributesNomisApi.stubPutPhysicalAttributes(HttpStatus.BAD_GATEWAY)

        awsSnsClient.publish(physicalAttributesRequest()).get()
          .also {
            // event should end up on the DLQ
            await untilCallTo {
              prisonPersonDlqClient!!.countAllMessagesOnQueue(prisonPersonDlqUrl!!).get()
            } matches { it == 1 }
          }

        // should call DPS and NOMIS APIs for each retry
        dpsApi.verify(3, getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")))
        physicalAttributesNomisApi.verify(3, putRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")))
        // should publish failed telemetry for each retry
        verify(telemetryClient, times(3)).trackEvent(
          eq("physical-attributes-update-error"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A1234AA",
                "reason" to "502 Bad Gateway from PUT http://localhost:8082/prisoners/A1234AA/physical-attributes",
                "fields" to "null",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `should fail for unknown profile type`() = runTest {
        dpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA")

        awsSnsClient.publish(physicalAttributesRequest(fields = "[UNKNOWN]")).get()
          .also {
            // event should end up on the DLQ
            await untilCallTo {
              prisonPersonDlqClient!!.countAllMessagesOnQueue(prisonPersonDlqUrl!!).get()
            } matches { it == 1 }
          }

        verify(telemetryClient, times(3)).trackEvent(
          eq("physical-attributes-update-error"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A1234AA",
                "reason" to "Unknown field: UNKNOWN",
                "fields" to "[UNKNOWN]",
              ),
            )
          },
          isNull(),
        )
      }
    }
  }

  private fun physicalAttributesRequest(prisonerNumber: String = "A1234AA", source: String = "DPS", fields: String? = null) =
    PublishRequest.builder().topicArn(topicArn)
      .message(physicalAttributesMessage(prisonerNumber, source, fields))
      .messageAttributes(
        mapOf(
          "eventType" to MessageAttributeValue.builder().dataType("String")
            .stringValue("prison-person.physical-attributes.updated").build(),
        ),
      ).build()

  private fun physicalAttributesMessage(prisonerNumber: String, source: String, fields: String?) =
    (
      fields
        ?.let { ""","fields": "$fields"""" }
        ?: ""
      ).let { fieldsJson ->
      """
          {
            "eventType":"prison-person.physical-attributes.updated",
            "additionalInformation": {
              "url":"https://prison-person-api-dev.prison.service.justice.gov.uk/prisoners/$prisonerNumber",
              "source":"$source",
              "prisonerNumber":"$prisonerNumber"
              $fieldsJson
            },
            "occurredAt":"2024-08-12T15:16:44.356649821+01:00",
            "description":"The physical attributes of a prisoner have been updated.",
            "version":"1.0"
          }
      """.trimIndent()
    }

  @Nested
  inner class UpdatePhysicalAttributesApi {

    @Test
    fun `should update physical attributes`() {
      dpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA")
      physicalAttributesNomisApi.stubPutPhysicalAttributes(offenderNo = "A1234AA")
      profileDetailsNomisApi.stubPutProfileDetails(offenderNo = "A1234AA")

      webTestClient.put().uri("/prisonperson/A1234AA/physical-attributes?fields=HEIGHT&fields=BUILD")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_PRISON_PERSON")))
        .exchange()
        .expectStatus().isOk

      verifyDpsApiCall()
      verifyNomisPutPhysicalAttributes(height = equalTo("180"), weight = equalTo("80"))
      verifyNomisPutProfileDetails(profileType = equalTo("BUILD"), profileCode = equalTo("SMALL"))
      verify(telemetryClient).trackEvent(
        eq("physical-attributes-update-requested"),
        check {
          assertThat(it).containsExactlyEntriesOf(mapOf("offenderNo" to "A1234AA", "fields" to "[HEIGHT, BUILD]"))
        },
        isNull(),
      )
      verifyTelemetry("physical-attributes-update-success", "[HEIGHT]")
      verifyTelemetry("profile-details-update-success", "[BUILD]")
    }

    @Test
    fun `should fail on error`() = runTest {
      dpsApi.stubGetPhysicalAttributes(HttpStatus.BAD_REQUEST)

      webTestClient.put().uri("/prisonperson/A1234AA/physical-attributes")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_PRISON_PERSON")))
        .exchange()
        .expectStatus().is5xxServerError

      verifyDpsApiCall()
      // should never call NOMIS API
      physicalAttributesNomisApi.verify(0, putRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")))
      // should publish failed telemetry
      verify(telemetryClient).trackEvent(
        eq("physical-attributes-update-requested"),
        check {
          assertThat(it).containsExactlyEntriesOf(mapOf("offenderNo" to "A1234AA", "fields" to "null"))
        },
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("physical-attributes-update-error"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "offenderNo" to "A1234AA",
              "reason" to "400 Bad Request from GET http://localhost:8097/prisoners/A1234AA/physical-attributes",
              "fields" to "null",
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
      physicalAttributesNomisApi.stubPutPhysicalAttributes(offenderNo = "A1234AA")

      webTestClient.put().uri("/prisonperson/A1234AA/physical-attributes")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_PRISON_PERSON__RECONCILIATION")))
        .exchange()
        .expectStatus().isOk
    }
  }

  private fun verifyDpsApiCall() {
    dpsApi.verify(
      getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")),
    )
  }

  private fun verifyNomisPutPhysicalAttributes(height: StringValuePattern, weight: StringValuePattern) {
    physicalAttributesNomisApi.verify(
      putRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes"))
        .withRequestBody(matchingJsonPath("height", height))
        .withRequestBody(matchingJsonPath("weight", weight)),
    )
  }

  private fun verifyNomisPutProfileDetails(profileType: StringValuePattern, profileCode: StringValuePattern) {
    profileDetailsNomisApi.verify(
      putRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details"))
        .withRequestBody(matchingJsonPath("profileType", profileType))
        .withRequestBody(matchingJsonPath("profileCode", profileCode)),
    )
  }

  private fun verifyTelemetry(event: String, fields: String) {
    verify(telemetryClient).trackEvent(
      eq(event),
      check {
        assertThat(it).containsExactlyInAnyOrderEntriesOf(
          mapOf(
            "offenderNo" to "A1234AA",
            "bookingId" to "12345",
            "created" to "true",
            "fields" to fields,
          ),
        )
      },
      isNull(),
    )
  }
}