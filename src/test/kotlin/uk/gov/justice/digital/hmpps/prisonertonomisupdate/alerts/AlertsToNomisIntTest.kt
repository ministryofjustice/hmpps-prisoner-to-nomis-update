package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.AlertsDpsApiExtension.Companion.alertsDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.model.Comment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AlertMappingDto
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class AlertsToNomisIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var alertsNomisApi: AlertsNomisApiMockServer

  @Autowired
  private lateinit var alertsMappingApi: AlertsMappingApiMockServer

  @Nested
  @DisplayName("prisoner-alerts.alert-created")
  inner class AlertCreated {
    @Nested
    @DisplayName("when NOMIS is the origin of a Alert create")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setup() {
        publishCreateAlertDomainEvent(source = AlertSource.NOMIS)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing it ignore the create`() {
        verify(telemetryClient).trackEvent(
          eq("alert-create-ignored"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will not try to create the Alert in NOMIS`() {
        alertsNomisApi.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Alert create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val offenderNo = "A1234KT"
        private val dpsAlertId = UUID.randomUUID().toString()
        private val nomisBookingId = 43217L
        private val nomisAlertSequence = 3L

        @BeforeEach
        fun setUp() {
          alertsMappingApi.stubGetByDpsId(HttpStatus.NOT_FOUND)
          alertsDpsApi.stubGetAlert(
            dpsAlert().copy(
              alertUuid = UUID.fromString(dpsAlertId),
              prisonNumber = offenderNo,
              alertCode = dpsAlert().alertCode.copy(code = "HPI"),
              activeFrom = LocalDate.parse("2023-03-03"),
              activeTo = LocalDate.parse("2023-06-06"),
              isActive = false,
              createdBy = "BOBBY.BEANS",
              authorisedBy = "Security Team",
              description = "Added for good reasons",
            ),
          )
          alertsNomisApi.stubPostAlert(offenderNo, alert = createAlertResponse(bookingId = nomisBookingId, alertSequence = nomisAlertSequence))
          alertsMappingApi.stubPostMapping()
          publishCreateAlertDomainEvent(offenderNo = offenderNo, alertUuid = dpsAlertId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("alert-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the alert created`() {
          verify(telemetryClient).trackEvent(
            eq("alert-create-success"),
            check {
              assertThat(it).containsEntry("dpsAlertId", dpsAlertId)
              assertThat(it).containsEntry("offenderNo", offenderNo)
              assertThat(it).containsEntry("alertCode", "HPI")
              assertThat(it).containsEntry("nomisAlertSequence", "$nomisAlertSequence")
              assertThat(it).containsEntry("nomisBookingId", "$nomisBookingId")
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get alert details`() {
          alertsDpsApi.verify(getRequestedFor(urlMatching("/alerts/$dpsAlertId")))
        }

        @Test
        fun `will create the alert in NOMIS`() {
          alertsNomisApi.verify(postRequestedFor(urlEqualTo("/prisoners/$offenderNo/alerts")))
        }

        @Test
        fun `the created alert will contain details of the DPS alert`() {
          alertsNomisApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("alertCode", "HPI")
              .withRequestBodyJsonPath("date", "2023-03-03")
              .withRequestBodyJsonPath("isActive", false)
              .withRequestBodyJsonPath("createUsername", "BOBBY.BEANS")
              .withRequestBodyJsonPath("expiryDate", "2023-06-06")
              .withRequestBodyJsonPath("comment", "Added for good reasons")
              .withRequestBodyJsonPath("authorisedBy", "Security Team"),
          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids`() {
          alertsMappingApi.verify(postRequestedFor(urlEqualTo("/mapping/alerts")))
        }

        @Test
        fun `the created mapping will contain the IDs`() {
          alertsMappingApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("dpsAlertId", dpsAlertId)
              .withRequestBodyJsonPath("nomisBookingId", nomisBookingId)
              .withRequestBodyJsonPath("nomisAlertSequence", nomisAlertSequence)
              .withRequestBodyJsonPath("mappingType", AlertMappingDto.MappingType.DPS_CREATED.name),
          )
        }
      }

      @Nested
      @DisplayName("when the create of the mapping fails")
      inner class WithCreateMappingFailures {
        private val offenderNo = "A1234KT"
        private val dpsAlertId = UUID.randomUUID().toString()
        private val nomisBookingId = 43217L
        private val nomisAlertSequence = 3L

        @BeforeEach
        fun setUp() {
          alertsMappingApi.stubGetByDpsId(HttpStatus.NOT_FOUND)
          alertsDpsApi.stubGetAlert(dpsAlert().copy(alertUuid = UUID.fromString(dpsAlertId)))
          alertsNomisApi.stubPostAlert(offenderNo, alert = createAlertResponse(bookingId = nomisBookingId, alertSequence = nomisAlertSequence))
        }

        @Nested
        @DisplayName("fails once")
        inner class MappingFailsOnce {
          @BeforeEach
          fun setUp() {
            alertsMappingApi.stubPostMappingFollowedBySuccess(HttpStatus.INTERNAL_SERVER_ERROR)
            publishCreateAlertDomainEvent(offenderNo = offenderNo, alertUuid = dpsAlertId)
          }

          @Test
          fun `will eventually send telemetry for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("alert-create-success"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will create the alert in NOMIS once`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("alert-create-success"),
                any(),
                isNull(),
              )
            }

            alertsNomisApi.verify(1, postRequestedFor(urlEqualTo("/prisoners/$offenderNo/alerts")))
          }

          @Test
          fun `telemetry will contain key facts about the alert created`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("alert-create-success"),
                check {
                  assertThat(it).containsEntry("dpsAlertId", dpsAlertId)
                  assertThat(it).containsEntry("offenderNo", offenderNo)
                  assertThat(it).containsEntry("alertCode", "HPI")
                  assertThat(it).containsEntry("nomisAlertSequence", "$nomisAlertSequence")
                  assertThat(it).containsEntry("nomisBookingId", "$nomisBookingId")
                },
                isNull(),
              )
            }
          }
        }

        @Nested
        @DisplayName("always fails")
        inner class MappingAlwaysFails {
          @BeforeEach
          fun setUp() {
            alertsMappingApi.stubPostMapping(HttpStatus.INTERNAL_SERVER_ERROR)
            publishCreateAlertDomainEvent(offenderNo = offenderNo, alertUuid = dpsAlertId)
          }

          @Test
          fun `will add message to dead letter queue`() {
            await untilCallTo {
              awsSqsAlertsDlqClient!!.countAllMessagesOnQueue(alertsDlqUrl!!).get()
            } matches { it == 1 }
          }

          @Test
          fun `will create the alert in NOMIS once`() {
            await untilCallTo {
              awsSqsAlertsDlqClient!!.countAllMessagesOnQueue(alertsDlqUrl!!).get()
            } matches { it == 1 }

            alertsNomisApi.verify(1, postRequestedFor(urlEqualTo("/prisoners/$offenderNo/alerts")))
          }
        }
      }

      @Nested
      @DisplayName("when alert has already been created")
      inner class WhenAlertAlreadyCreated {
        private val dpsAlertId = UUID.randomUUID().toString()
        private val nomisBookingId = 43217L
        private val nomisAlertSequence = 3L
        private val offenderNo = "A1234KT"

        @BeforeEach
        fun setUp() {
          alertsMappingApi.stubGetByDpsId(
            dpsAlertId = dpsAlertId,
            AlertMappingDto(
              dpsAlertId = dpsAlertId,
              nomisBookingId = nomisBookingId,
              nomisAlertSequence = nomisAlertSequence,
              mappingType = AlertMappingDto.MappingType.DPS_CREATED,
            ),
          )
          publishCreateAlertDomainEvent(offenderNo = offenderNo, alertUuid = dpsAlertId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `it will not call back to DPS API`() {
          alertsDpsApi.verify(0, getRequestedFor(anyUrl()))
        }

        @Test
        fun `it will not create the alert again in NOMIS`() {
          alertsNomisApi.verify(0, postRequestedFor(anyUrl()))
        }
      }
    }
  }

  @Nested
  @DisplayName("prisoner-alerts.alert-updated")
  inner class AlertUpdated {
    @Nested
    @DisplayName("when NOMIS is the origin of the Alert update")
    inner class WhenNomisUpdated {
      @BeforeEach
      fun setup() {
        publishUpdateAlertDomainEvent(source = AlertSource.NOMIS)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing it ignored the update`() {
        verify(telemetryClient).trackEvent(
          eq("alert-updated-ignored"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will not try to update the Alert in NOMIS`() {
        alertsNomisApi.verify(0, putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the Alert update")
    inner class WhenDpsUpdated {
      @Nested
      @DisplayName("when no mapping found")
      inner class WhenNoMappingFound {
        @BeforeEach
        fun setUp() {
          alertsMappingApi.stubGetByDpsId(HttpStatus.NOT_FOUND)
          publishUpdateAlertDomainEvent()
        }

        @Test
        fun `will treat this as an error and message will go on DLQ`() {
          await untilCallTo {
            awsSqsAlertsDlqClient!!.countAllMessagesOnQueue(alertsDlqUrl!!).get()
          } matches { it == 1 }
        }

        @Test
        fun `will send telemetry event showing it failed to update for each retry`() {
          await untilAsserted {
            verify(telemetryClient, times(3)).trackEvent(
              eq("alert-updated-failed"),
              any(),
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("when mapping is found")
      inner class WhenMappingIsFound {
        private val dpsAlertId = UUID.randomUUID().toString()
        private val nomisBookingId = 43217L
        private val nomisAlertSequence = 3L
        private val offenderNo = "A1234KT"

        @BeforeEach
        fun setUp() {
          alertsMappingApi.stubGetByDpsId(
            dpsAlertId,
            AlertMappingDto(
              dpsAlertId = dpsAlertId,
              nomisBookingId = nomisBookingId,
              nomisAlertSequence = nomisAlertSequence,
              mappingType = AlertMappingDto.MappingType.DPS_CREATED,
            ),
          )
          alertsDpsApi.stubGetAlert(
            alert = dpsAlert().copy(
              alertUuid = UUID.fromString(dpsAlertId),
              activeFrom = LocalDate.parse("2020-07-19"),
              activeTo = LocalDate.parse("2023-07-19"),
              isActive = false,
              authorisedBy = "Rasheed",
              lastModifiedBy = "RASHEED.BAKE",
              comments = listOf(
                Comment(
                  UUID.randomUUID(),
                  comment = "The only comment",
                  createdBy = "SOMEONE",
                  createdAt = LocalDateTime.now(),
                  createdByDisplayName = "Some One",
                ),
              ),
              description = "Alert added for good reasons",
              alertCode = dpsAlert().alertCode.copy(code = "HPI"),
            ),
          )
          alertsNomisApi.stubPutAlert(bookingId = nomisBookingId, alertSequence = nomisAlertSequence)
          publishUpdateAlertDomainEvent(alertUuid = dpsAlertId, offenderNo = offenderNo)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the update`() {
          verify(telemetryClient).trackEvent(
            eq("alert-updated-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the updated alert`() {
          verify(telemetryClient).trackEvent(
            eq("alert-updated-success"),
            check {
              assertThat(it).containsEntry("dpsAlertId", dpsAlertId)
              assertThat(it).containsEntry("offenderNo", offenderNo)
              assertThat(it).containsEntry("alertCode", "HPI")
              assertThat(it).containsEntry("nomisAlertSequence", "$nomisAlertSequence")
              assertThat(it).containsEntry("nomisBookingId", "$nomisBookingId")
            },
            isNull(),
          )
        }

        @Test
        fun `will call the mapping service to get the NOMIS alert id`() {
          alertsMappingApi.verify(getRequestedFor(urlMatching("/mapping/alerts/dps-alert-id/$dpsAlertId")))
        }

        @Test
        fun `will call back to DPS to get alert details`() {
          alertsDpsApi.verify(getRequestedFor(urlMatching("/alerts/$dpsAlertId")))
        }

        @Test
        fun `will update the alert in NOMIS`() {
          alertsNomisApi.verify(putRequestedFor(urlEqualTo("/prisoners/booking-id/$nomisBookingId/alerts/$nomisAlertSequence")))
        }

        @Test
        fun `the update alert will contain details of the DPS alert`() {
          alertsNomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("date", "2020-07-19")
              .withRequestBodyJsonPath("isActive", false)
              .withRequestBodyJsonPath("updateUsername", "RASHEED.BAKE")
              .withRequestBodyJsonPath("expiryDate", "2023-07-19")
              // TODO likely to do something when there are loads of comments for now use description
              .withRequestBodyJsonPath("comment", "Alert added for good reasons")
              .withRequestBodyJsonPath("authorisedBy", "Rasheed"),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("prisoner-alerts.alert-deleted")
  inner class AlertDeleted {
    @Nested
    @DisplayName("when NOMIS is the origin of the Alert delete")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setup() {
        publishDeleteAlertDomainEvent(source = AlertSource.NOMIS)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing it ignored the update`() {
        verify(telemetryClient).trackEvent(
          eq("alert-deleted-ignored"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will not try to delete the Alert in NOMIS`() {
        alertsNomisApi.verify(0, deleteRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the Alert delete")
    inner class WhenDpsDeleted {
      @Nested
      @DisplayName("when no mapping found")
      inner class WhenNoMappingFound {
        @BeforeEach
        fun setUp() {
          alertsMappingApi.stubGetByDpsId(HttpStatus.NOT_FOUND)
          publishDeleteAlertDomainEvent()
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will ignore request since delete may have happened already by previous event`() {
          verify(telemetryClient).trackEvent(
            eq("alert-deleted-skipped"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when mapping is found")
      inner class WhenMappingIsFound {
        private val dpsAlertId = UUID.randomUUID().toString()
        private val nomisBookingId = 43217L
        private val nomisAlertSequence = 3L
        private val offenderNo = "A1234KT"

        @BeforeEach
        fun setUp() {
          alertsMappingApi.stubGetByDpsId(
            dpsAlertId,
            AlertMappingDto(
              dpsAlertId = dpsAlertId,
              nomisBookingId = nomisBookingId,
              nomisAlertSequence = nomisAlertSequence,
              mappingType = AlertMappingDto.MappingType.DPS_CREATED,
            ),
          )
          alertsNomisApi.stubDeleteAlert(bookingId = nomisBookingId, alertSequence = nomisAlertSequence)
          alertsMappingApi.stubDeleteByDpsId(dpsAlertId)
          publishDeleteAlertDomainEvent(alertUuid = dpsAlertId, offenderNo = offenderNo)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the delete`() {
          verify(telemetryClient).trackEvent(
            eq("alert-deleted-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the deleted alert`() {
          verify(telemetryClient).trackEvent(
            eq("alert-deleted-success"),
            check {
              assertThat(it).containsEntry("dpsAlertId", dpsAlertId)
              assertThat(it).containsEntry("offenderNo", offenderNo)
              assertThat(it).containsEntry("alertCode", "HPI")
              assertThat(it).containsEntry("nomisAlertSequence", "$nomisAlertSequence")
              assertThat(it).containsEntry("nomisBookingId", "$nomisBookingId")
            },
            isNull(),
          )
        }

        @Test
        fun `will call the mapping service to get the NOMIS alert id`() {
          alertsMappingApi.verify(getRequestedFor(urlMatching("/mapping/alerts/dps-alert-id/$dpsAlertId")))
        }

        @Test
        fun `will delete the alert in NOMIS`() {
          alertsNomisApi.verify(deleteRequestedFor(urlEqualTo("/prisoners/booking-id/$nomisBookingId/alerts/$nomisAlertSequence")))
        }

        @Test
        fun `will delete the alert mapping`() {
          alertsMappingApi.verify(deleteRequestedFor(urlEqualTo("/mapping/alerts/dps-alert-id/$dpsAlertId")))
        }
      }

      @Nested
      @DisplayName("when mapping delete fails")
      inner class WhenMappingDeleteFails {
        private val dpsAlertId = UUID.randomUUID().toString()
        private val nomisBookingId = 43217L
        private val nomisAlertSequence = 3L
        private val offenderNo = "A1234KT"

        @BeforeEach
        fun setUp() {
          alertsMappingApi.stubGetByDpsId(
            dpsAlertId,
            AlertMappingDto(
              dpsAlertId = dpsAlertId,
              nomisBookingId = nomisBookingId,
              nomisAlertSequence = nomisAlertSequence,
              mappingType = AlertMappingDto.MappingType.DPS_CREATED,
            ),
          )
          alertsNomisApi.stubDeleteAlert(bookingId = nomisBookingId, alertSequence = nomisAlertSequence)
          alertsMappingApi.stubDeleteByDpsId(status = HttpStatus.INTERNAL_SERVER_ERROR)
          publishDeleteAlertDomainEvent(alertUuid = dpsAlertId, offenderNo = offenderNo)
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("alert-deleted-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will delete the alert in NOMIS`() {
          alertsNomisApi.verify(deleteRequestedFor(urlEqualTo("/prisoners/booking-id/$nomisBookingId/alerts/$nomisAlertSequence")))
        }

        @Test
        fun `will try delete the alert mapping once and ignore failure`() {
          alertsMappingApi.verify(deleteRequestedFor(urlEqualTo("/mapping/alerts/dps-alert-id/$dpsAlertId")))
          verify(telemetryClient).trackEvent(
            eq("alert-mapping-deleted-failed"),
            any(),
            isNull(),
          )
        }
      }
    }
  }

  private fun publishCreateAlertDomainEvent(
    offenderNo: String = "A1234KT",
    alertUuid: String = UUID.randomUUID().toString(),
    source: AlertSource = AlertSource.DPS,
    alertCode: String = "HPI",
  ) {
    publishAlertDomainEvent("prisoner-alerts.alert-created", offenderNo, alertUuid, source, alertCode)
  }

  private fun publishUpdateAlertDomainEvent(
    offenderNo: String = "A1234KT",
    alertUuid: String = UUID.randomUUID().toString(),
    source: AlertSource = AlertSource.DPS,
    alertCode: String = "HPI",
  ) {
    publishAlertDomainEvent("prisoner-alerts.alert-updated", offenderNo, alertUuid, source, alertCode)
  }

  private fun publishDeleteAlertDomainEvent(
    offenderNo: String = "A1234KT",
    alertUuid: String = UUID.randomUUID().toString(),
    source: AlertSource = AlertSource.DPS,
    alertCode: String = "HPI",
  ) {
    publishAlertDomainEvent("prisoner-alerts.alert-deleted", offenderNo, alertUuid, source, alertCode)
  }

  private fun publishAlertDomainEvent(
    eventType: String,
    offenderNo: String,
    alertUuid: String,
    source: AlertSource,
    alertCode: String,
  ) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          alertMessagePayload(
            eventType = eventType,
            offenderNo = offenderNo,
            alertUuid = alertUuid,
            source = source,
            alertCode = alertCode,
          ),
        )
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }
}

fun alertMessagePayload(
  eventType: String,
  offenderNo: String,
  alertUuid: String,
  source: AlertSource,
  alertCode: String,
) =
  //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "url":"https://somecallback", 
        "alertUuid": "$alertUuid",
        "prisonNumber": "$offenderNo", 
        "source": "${source.name}",
        "alertCode": "$alertCode"
      }
    }
    """

fun RequestPatternBuilder.withRequestBodyJsonPath(path: String, pattern: StringValuePattern): RequestPatternBuilder =
  this.withRequestBody(matchingJsonPath(path, pattern))

fun RequestPatternBuilder.withRequestBodyJsonPath(path: String, equalTo: Any): RequestPatternBuilder =
  this.withRequestBodyJsonPath(path, equalTo(equalTo.toString()))
