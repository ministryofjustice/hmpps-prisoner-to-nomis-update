package uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers

import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTestContextBootstrapper
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.BootstrapWith
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.AlertsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.CaseNotesDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AdjudicationsApiExtension
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.DpsApiExtension as PrisonPersonDpsApiExtension

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
  NomisApiExtension::class,
  MappingExtension::class,
  HmppsAuthApiExtension::class,
  VisitsApiExtension::class,
  IncentivesApiExtension::class,
  ActivitiesApiExtension::class,
  AppointmentsApiExtension::class,
  SentencingAdjustmentsApiExtension::class,
  AdjudicationsApiExtension::class,
  NonAssociationsApiExtension::class,
  LocationsApiExtension::class,
  AlertsDpsApiExtension::class,
  CaseNotesDpsApiExtension::class,
  PrisonPersonDpsApiExtension::class,
)
@ActiveProfiles("test")
@SpringBootTest(classes = [JacksonAutoConfiguration::class, CodecsAutoConfiguration::class, WebClientConfiguration::class, WebClientAutoConfiguration::class, ReactiveOAuth2ClientAutoConfiguration::class, ReactiveSecurityAutoConfiguration::class, HmppsReactiveWebClientConfiguration::class])
@BootstrapWith(SpringBootTestContextBootstrapper::class)
annotation class SpringAPIServiceTest
