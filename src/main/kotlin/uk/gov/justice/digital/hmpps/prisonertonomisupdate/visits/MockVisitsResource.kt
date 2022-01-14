package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import io.swagger.v3.oas.annotations.Operation
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

@RestController
class MockVisitsResource {
  @PreAuthorize("hasRole('ROLE_VISIT_SCHEDULER')")
  @GetMapping("/visits/{visitId}")
  @Operation(hidden = true)
  fun getVisit(
    @PathVariable visitId: String,
  ): VSIPVisit = VSIPVisit(visitId = visitId)
}

data class VSIPVisit(
  val visitId: String = "101",
  val prisonId: String = "MDI",
  val visitType: String = "STANDARD_SOCIAL",
  val visitRoom: String = "Room 1",
  val visitDate: LocalDate = LocalDate.now().plusDays(1),
  val startTime: LocalTime = LocalTime.now(),
  val endTime: LocalTime = LocalTime.now().plusHours(1),
  val visitors: List<VSIPVisitor> = listOf(VSIPVisitor("4729570"), VSIPVisitor("4729550")),
  val currentStatus: String = "BOOKED",
  val lastRevisedTimestamp: OffsetDateTime = OffsetDateTime.now(),
) {
  data class VSIPVisitor(val nomisPersonId: String)
}
