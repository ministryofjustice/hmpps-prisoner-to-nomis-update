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
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import javax.validation.Valid

@RestController
class MockVisitsResource(private val mockVisitsData: MockVisitsData) {
  @PreAuthorize("hasRole('ROLE_VISIT_SCHEDULER')")
  @GetMapping("/visits/{visitId}")
  @Operation(hidden = true)
  fun getVisit(
    @PathVariable visitId: String,
  ): VSIPVisit = VSIPVisit(visitId = visitId, visitsData = mockVisitsData)

  @PreAuthorize("hasRole('ROLE_VISIT_SCHEDULER')")
  @PostMapping("/migrate-visits")
  @Operation(hidden = true)
  fun createVisit(
    @RequestBody @Valid createVisitRequest: CreateVisitRequest
  ): String = UUID.randomUUID().toString()
}

data class CreateVisitRequest(
  val prisonerId: String,
  val prisonId: String,
  val visitRoom: String,
  val visitType: String,
  val outcomeStatus: String?,
  val visitStatus: String,
  val visitRestriction: String,
  val startTimestamp: LocalDateTime,
  val endTimestamp: LocalDateTime,
  val legacyData: CreateLegacyDateOnVisit?,
  val visitContact: CreateContactOnVisit?,
  val visitors: List<CreateVisitorOnVisit>? = listOf(),
  val visitNotes: List<CreateNotesOnVisit>? = listOf(),
)

data class CreateVisitorOnVisit(
  val nomisPersonId: Long,
  val leadVisitor: Boolean = false
)

data class CreateContactOnVisit(
  val name: String?,
  val telephone: String?
)

data class CreateLegacyDateOnVisit(
  val leadVisitorId: Long,
)

data class CreateNotesOnVisit(
  val type: String,
  val text: String,
)

data class VSIPVisit(
  val reference: String,
  val prisonId: String,
  val prisonerId: String,
  val visitors: List<VSIPVisitor>,
  val visitType: String = "SOCIAL",
  val startTimestamp: LocalDateTime = LocalDateTime.now(),
  val endTimestamp: LocalDateTime = LocalDateTime.now().plusHours(1),
  val visitStatus: String = "BOOKED",
  val lastRevisedTimestamp: OffsetDateTime = OffsetDateTime.now(),
) {
  constructor(visitId: String, visitsData: MockVisitsData) : this(
    reference = visitId,
    prisonId = visitsData.prisonId,
    prisonerId = visitsData.prisonerId,
    visitors = visitsData.visitors.map { VSIPVisitor(it) },
  )

  data class VSIPVisitor(val nomisPersonId: String)
}

@Configuration
@ConfigurationProperties(prefix = "mock.visits")
data class MockVisitsData(
  var prisonId: String = "MDI",
  var visitors: List<String> = listOf("4729570", "4729550"),
  var prisonerId: String = "A7948DY"
)
