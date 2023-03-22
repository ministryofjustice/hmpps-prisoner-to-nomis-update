package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AppointmentsApiExtension.Companion.appointmentsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

const val APPOINTMENT_INSTANCE_ID = 1234567L
const val BOOKING_ID = 987651L
const val LOCATION_ID = 987651L
const val EVENT_ID = 111222333L

class AppointmentsToNomisIntTest : SqsIntegrationTestBase() {

  private val appointmentResponse = """{
      "id": $APPOINTMENT_INSTANCE_ID,
      "bookingId": $BOOKING_ID,
      "internalLocationId": $LOCATION_ID,
      "appointmentDate": "2023-03-14",
      "startTime": "10:15",
      "endTime":  "11:42",
      "category": {
        "id": 1919,
        "active": true,
        "code": "MEDI",
        "description": "Medical - Initial assessment"
      },
      "prisonCode": "SKI",
      "inCell": false,
      "prisonerNumber": "A1234BC",
      "cancelled": false
    }
  """.trimIndent()

  private val mappingResponse = """{
      "appointmentInstanceId": $APPOINTMENT_INSTANCE_ID,
      "nomisEventId": $EVENT_ID
    }
  """.trimIndent()

  @Nested
  inner class CreateAppointment {
    @Nested
    inner class WhenAppointmentHasJustBeenCreatedByAppointmentService {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(APPOINTMENT_INSTANCE_ID, 404)
        mappingServer.stubCreateAppointment()
      }

      @Nested
      inner class WhenAppointmentIsProvided {

        @BeforeEach
        fun setUp() {
          nomisApi.stubAppointmentCreate("""{ "eventId": $EVENT_ID }""")
          appointmentsApi.stubGetAppointmentInstance(id = APPOINTMENT_INSTANCE_ID, response = appointmentResponse)
          publishCreateDomainEvent()
        }

        @Test
        fun `will callback back to appointment service to get more details`() {
          await untilAsserted {
            appointmentsApi.verify(getRequestedFor(urlEqualTo("/appointment-instance-details/$APPOINTMENT_INSTANCE_ID")))
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will create an appointment in NOMIS`() {
          await untilAsserted {
            nomisApi.verify(
              postRequestedFor(urlEqualTo("/appointments"))
                .withRequestBody(matchingJsonPath("bookingId", equalTo("$BOOKING_ID")))
                .withRequestBody(matchingJsonPath("internalLocationId", equalTo("$LOCATION_ID")))
                .withRequestBody(matchingJsonPath("eventDate", equalTo("2023-03-14")))
                .withRequestBody(matchingJsonPath("startTime", equalTo("10:15")))
                .withRequestBody(matchingJsonPath("endTime", equalTo("11:42")))
                .withRequestBody(matchingJsonPath("eventSubType", equalTo("MEDI"))),
            )
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will create a mapping between the two appointments`() {
          await untilAsserted {
            mappingServer.verify(
              postRequestedFor(urlEqualTo("/mapping/appointments"))
                .withRequestBody(matchingJsonPath("nomisEventId", equalTo(EVENT_ID.toString())))
                .withRequestBody(
                  matchingJsonPath(
                    "appointmentInstanceId",
                    equalTo(APPOINTMENT_INSTANCE_ID.toString()),
                  ),
                ),
            )
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will create success telemetry`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("appointment-create-success"),
              check {
                assertThat(it["appointmentInstanceId"]).isEqualTo(APPOINTMENT_INSTANCE_ID.toString())
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["locationId"]).isEqualTo(LOCATION_ID.toString())
                assertThat(it["date"]).isEqualTo("2023-03-14")
                assertThat(it["start"]).isEqualTo("10:15")
                assertThat(it["nomisEventId"]).isEqualTo(EVENT_ID.toString())
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    inner class WhenMappingAlreadyCreated {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetMappingGivenAppointmentInstanceId(APPOINTMENT_INSTANCE_ID, mappingResponse)
      }

      @Nested
      inner class WhenAppointmentIsProvided {

        @BeforeEach
        fun setUp() {
          appointmentsApi.stubGetAppointmentInstance(id = APPOINTMENT_INSTANCE_ID, response = appointmentResponse)

          publishCreateDomainEvent()
        }

        @Test
        fun `will callback back to mapping service to check message has not already been processed`() {
          await untilAsserted {
            mappingServer.verify(
              getRequestedFor(urlEqualTo("/mapping/appointments/appointment-instance-id/$APPOINTMENT_INSTANCE_ID")),
            )
          }
        }

        @Test
        fun `will not create an appointment in NOMIS`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("appointment-create-duplicate"),
              any(),
              isNull(),
            )
          }
          nomisApi.verify(0, postRequestedFor(urlEqualTo("/appointments")))
        }
      }
    }

    @Nested
    inner class ExceptionHandling {

      @Nested
      inner class WhenAppointmentServiceFailsOnce {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(APPOINTMENT_INSTANCE_ID, 404)
          mappingServer.stubCreateAppointment()
          nomisApi.stubAppointmentCreate("""{ "eventId": $EVENT_ID }""")
          appointmentsApi.stubGetAppointmentInstanceWithErrorFollowedBySlowSuccess(
            id = APPOINTMENT_INSTANCE_ID,
            response = appointmentResponse,
          )
          publishCreateDomainEvent()
        }

        @Test
        fun `will callback back to appointment service twice to get more details`() {
          await untilAsserted {
            appointmentsApi.verify(2, getRequestedFor(urlEqualTo("/appointment-instance-details/$APPOINTMENT_INSTANCE_ID")))
          }
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("appointment-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually create an appointment in NOMIS`() {
          await untilAsserted {
            nomisApi.verify(
              1,
              postRequestedFor(urlEqualTo("/appointments"))
                .withRequestBody(matchingJsonPath("bookingId", equalTo("$BOOKING_ID")))
                .withRequestBody(matchingJsonPath("internalLocationId", equalTo("$LOCATION_ID")))
                .withRequestBody(matchingJsonPath("eventDate", equalTo("2023-03-14")))
                .withRequestBody(matchingJsonPath("startTime", equalTo("10:15")))
                .withRequestBody(matchingJsonPath("endTime", equalTo("11:42")))
                .withRequestBody(matchingJsonPath("eventSubType", equalTo("MEDI"))),
            )
          }
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("appointment-create-success"),
              any(),
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenAppointmentServiceKeepsFailing {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(APPOINTMENT_INSTANCE_ID, 404)
          appointmentsApi.stubGetAppointmentInstanceWithError(
            APPOINTMENT_INSTANCE_ID,
            503,
          )
          await untilCallTo {
            awsSqsAppointmentDlqClient!!.countAllMessagesOnQueue(appointmentDlqUrl!!).get()
          } matches { it == 0 }

          publishCreateDomainEvent()
        }

        @Test
        fun `will callback back to appointment service 3 times before given up`() {
          await untilAsserted {
            appointmentsApi.verify(3, getRequestedFor(urlEqualTo("/appointment-instance-details/$APPOINTMENT_INSTANCE_ID")))
          }
        }

        @Test
        fun `will add message to dead letter queue`() {
          await untilCallTo {
            awsSqsAppointmentDlqClient!!.countAllMessagesOnQueue(appointmentDlqUrl!!).get()
          } matches { it == 1 }
        }
      }

      @Nested
      inner class WhenNomisServiceFailsOnce {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(APPOINTMENT_INSTANCE_ID, 404)
          mappingServer.stubCreateAppointment()
          nomisApi.stubAppointmentCreateWithErrorFollowedBySlowSuccess("""{ "eventId": $EVENT_ID }""")
          appointmentsApi.stubGetAppointmentInstance(id = APPOINTMENT_INSTANCE_ID, response = appointmentResponse)

          publishCreateDomainEvent()
        }

        @Test
        fun `will callback back to appointment api and NOMIS service twice`() {
          await untilAsserted {
            appointmentsApi.verify(2, getRequestedFor(urlEqualTo("/appointment-instance-details/$APPOINTMENT_INSTANCE_ID")))
          }
          await untilAsserted {
            nomisApi.verify(2, postRequestedFor(urlEqualTo("/appointments")))
          }
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("appointment-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually create a mapping after NOMIS appointment is created`() {
          await untilAsserted {
            mappingServer.verify(postRequestedFor(urlEqualTo("/mapping/appointments")))
          }
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("appointment-create-success"),
              any(),
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenNomisServiceKeepsFailing {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(APPOINTMENT_INSTANCE_ID, 404)
          appointmentsApi.stubGetAppointmentInstance(id = APPOINTMENT_INSTANCE_ID, response = appointmentResponse)

          nomisApi.stubAppointmentCreateWithError(503)
          await untilCallTo {
            awsSqsAppointmentDlqClient!!.countAllMessagesOnQueue(appointmentDlqUrl!!).get()
          } matches { it == 0 }

          publishCreateDomainEvent()
        }

        @Test
        fun `will callback back to appointment service 3 times before given up`() {
          await untilAsserted {
            nomisApi.verify(3, postRequestedFor(urlEqualTo("/appointments")))
          }
        }

        @Test
        fun `will add message to dead letter queue`() {
          await untilCallTo {
            awsSqsAppointmentDlqClient!!.countAllMessagesOnQueue(appointmentDlqUrl!!).get()
          } matches { it == 1 }
        }
      }

      @Nested
      inner class WhenMappingServiceFailsOnce {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(APPOINTMENT_INSTANCE_ID, 404)
          mappingServer.stubCreateAppointmentWithErrorFollowedBySlowSuccess()
          nomisApi.stubAppointmentCreate("""{ "eventId": $EVENT_ID }""")
          appointmentsApi.stubGetAppointmentInstance(id = APPOINTMENT_INSTANCE_ID, response = appointmentResponse)

          publishCreateDomainEvent()
        }

        @Test
        fun `should only create the NOMIS appointment once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("appointment-create-mapping-retry-success"),
              any(),
              isNull(),
            )
          }
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/appointments")))
        }

        @Test
        fun `will eventually create a mapping after NOMIS appointment is created`() {
          await untilAsserted {
            mappingServer.verify(
              2,
              postRequestedFor(urlEqualTo("/mapping/appointments")),
            )
          }
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("appointment-create-mapping-retry-success"),
              any(),
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenMappingServiceKeepsFailing {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(APPOINTMENT_INSTANCE_ID, 404)
          mappingServer.stubCreateAppointmentWithError(status = 503)
          nomisApi.stubAppointmentCreate("""{ "eventId": $EVENT_ID }""")
          appointmentsApi.stubGetAppointmentInstance(id = APPOINTMENT_INSTANCE_ID, response = appointmentResponse)

          await untilCallTo {
            awsSqsAppointmentDlqClient!!.countAllMessagesOnQueue(appointmentDlqUrl!!).get()
          } matches { it == 0 }

          publishCreateDomainEvent()
        }

        @Test
        fun `will try to create mapping 4 times and only create the NOMIS appointment once`() {
          await untilAsserted {
            mappingServer.verify(4, postRequestedFor(urlEqualTo("/mapping/appointments")))
          }
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/appointments")))
        }

        @Test
        fun `will add message to dead letter queue`() {
          await untilCallTo {
            awsSqsAppointmentDlqClient!!.countAllMessagesOnQueue(appointmentDlqUrl!!).get()
          } matches { it == 1 }
        }
      }
    }

    @Test
    fun `will log when duplicate is detected`() {
      appointmentsApi.stubGetAppointmentInstance(id = APPOINTMENT_INSTANCE_ID, response = appointmentResponse)
      mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(APPOINTMENT_INSTANCE_ID, 404)
      nomisApi.stubAppointmentCreate("""{ "eventId": $EVENT_ID }""")
      mappingServer.stubCreateAppointmentWithDuplicateError(
        appointmentInstanceId = APPOINTMENT_INSTANCE_ID,
        nomisEventId = EVENT_ID,
        duplicateNomisEventId = 999,
      )

      publishCreateDomainEvent()

      await untilAsserted {
        verify(telemetryClient).trackEvent(eq("appointment-mapping-create-failed"), any(), isNull())
      }
      await untilCallTo { appointmentsApi.getCountFor("/appointment-instance-details/$APPOINTMENT_INSTANCE_ID") } matches { it == 1 }
      await untilCallTo { nomisApi.postCountFor("/appointments") } matches { it == 1 }

      // the mapping call fails but is not queued for retry
      await untilCallTo {
        awsSqsAppointmentDlqClient!!.countAllMessagesOnQueue(appointmentDlqUrl!!).get()
      } matches { it == 0 }

      await untilCallTo { mappingServer.postCountFor("/mapping/appointments") } matches { it == 1 } // only tried once

      verify(telemetryClient).trackEvent(
        eq("to-nomis-synch-appointment-duplicate"),
        check {
          assertThat(it["appointmentInstanceId"]).isEqualTo("$APPOINTMENT_INSTANCE_ID")
          assertThat(it["existingNomisEventId"]).isEqualTo("$EVENT_ID")
          assertThat(it["duplicateAppointmentInstanceId"]).isEqualTo("$APPOINTMENT_INSTANCE_ID")
          assertThat(it["duplicateNomisEventId"]).isEqualTo("999")
        },
        isNull(),
      )
    }
  }

  private fun publishCreateDomainEvent() {
    val eventType = "appointments.appointment.created"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(appointmentMessagePayload(eventType, APPOINTMENT_INSTANCE_ID))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  fun appointmentMessagePayload(eventType: String, appointmentInstanceId: Long) =
    """{"eventType":"$eventType", "additionalInformation": { "id": $appointmentInstanceId }, "version": "1.0", "description": "description", "occurredAt": "2023-02-05T11:23:56.031Z"}"""
}
