package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import io.swagger.v3.oas.annotations.Operation
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

@RestController
class MockVisitsResource(private val mockVisitsData: MockVisitsData) {
  @PreAuthorize("hasRole('ROLE_VISIT_SCHEDULER')")
  @GetMapping("/visits/{visitId}")
  @Operation(hidden = true)
  fun getVisit(
    @PathVariable visitId: String,
  ): VSIPVisit = VSIPVisit(visitId = visitId, visitsData = mockVisitsData)

  @PreAuthorize("hasRole('ROLE_VISIT_SCHEDULER')")
  @PostMapping("/visits/{visitId}/nomis-mapping")
  @Operation(hidden = true)
  fun addVisitMapping(
    @PathVariable visitId: String,
    @RequestBody nomisData: VSISPNomisVisitData
  ) {
  }
}

data class VSIPVisit(
  val visitId: String,
  val prisonId: String,
  val visitors: List<VSIPVisitor>,
  val visitType: String = "STANDARD_SOCIAL",
  val visitRoom: String = "Room 1",
  val visitDate: LocalDate = LocalDate.now().plusDays(1),
  val startTime: LocalTime = LocalTime.now(),
  val endTime: LocalTime = LocalTime.now().plusHours(1),
  val currentStatus: String = "BOOKED",
  val lastRevisedTimestamp: OffsetDateTime = OffsetDateTime.now(),
) {
  constructor(visitId: String, visitsData: MockVisitsData) : this(
    visitId = visitId,
    prisonId = visitsData.prisonId,
    visitors = visitsData.visitors.map { VSIPVisitor(it) },
  )

  data class VSIPVisitor(val nomisPersonId: String)
}

data class VSISPNomisVisitData(val nomisVisitId: Long)

@Configuration
@ConfigurationProperties(prefix = "mock.visits")
data class MockVisitsData(
  var prisonId: String = "MDI",
  var visitors: List<String> = listOf("4729570", "4729550")
)
