package uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers

import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.http.codec.autoconfigure.CodecsAutoConfiguration
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.security.oauth2.client.autoconfigure.reactive.ReactiveOAuth2ClientAutoConfiguration
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTestContextBootstrapper
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.BootstrapWith
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.AdjudicationsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.AlertsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.CaseNotesDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonCprApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.CSIPDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.FinanceDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.IncidentsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances.VisitBalanceDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AppointmentsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.IncentivesApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.LocationsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NonAssociationsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.SentencingAdjustmentsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.VisitsApiExtension
import uk.gov.justice.hmpps.kotlin.auth.HmppsReactiveWebClientConfiguration
import java.lang.annotation.Inherited
import kotlin.annotation.AnnotationTarget.ANNOTATION_CLASS
import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Annotation for an API service test that focuses **only** on services that call a WebClient
 *
 *
 * Using this annotation will disable full auto-configuration and instead apply only
 *
 */
@Target(ANNOTATION_CLASS, CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Inherited
@ExtendWith(
  HmppsAuthApiExtension::class,
  MappingExtension::class,
  NomisApiExtension::class,
  ActivitiesApiExtension::class,
  AdjudicationsApiExtension::class,
  AlertsDpsApiExtension::class,
  AppointmentsApiExtension::class,
  CaseNotesDpsApiExtension::class,
  ContactPersonDpsApiExtension::class,
  CourtSentencingApiExtension::class,
  CSIPDpsApiExtension::class,
  FinanceDpsApiExtension::class,
  IncentivesApiExtension::class,
  IncidentsDpsApiExtension::class,
  LocationsApiExtension::class,
  NonAssociationsApiExtension::class,
  OrganisationsDpsApiExtension::class,
  SentencingAdjustmentsApiExtension::class,
  VisitBalanceDpsApiExtension::class,
  VisitsApiExtension::class,
  ExternalMovementsDpsApiExtension::class,
  CorePersonCprApiExtension::class,
  OfficialVisitsDpsApiExtension::class,
)
@ActiveProfiles("test")
@SpringBootTest(classes = [JacksonAutoConfiguration::class, CodecsAutoConfiguration::class, WebClientConfiguration::class, WebClientAutoConfiguration::class, ReactiveOAuth2ClientAutoConfiguration::class, HmppsReactiveWebClientConfiguration::class])
@AutoConfigureJson
@BootstrapWith(SpringBootTestContextBootstrapper::class)
annotation class SpringAPIServiceTest
