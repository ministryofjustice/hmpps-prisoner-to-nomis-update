package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

// This checks each element of `sources` exists in `targets` after transformation by `findTarget`
internal fun <SOURCE, TARGET> findMissing(
  sources: List<SOURCE>,
  targets: List<TARGET>,
  findTarget: (SOURCE) -> TARGET?,
) = sources.map { src -> src to findTarget(src) }
  .map { (src, trg) -> src to targets.contains(trg) }
  .filter { (_, found) -> !found }
  .map { (src, _) -> src }

// This finds each element of `sources` that exists in `targets` after transformation by `findTarget`
internal fun <SOURCE, TARGET> findMatches(
  sources: List<SOURCE>,
  targets: List<TARGET>,
  findTarget: (SOURCE) -> TARGET?,
): List<Pair<SOURCE, TARGET>> = sources.map { src -> src to findTarget(src) }
  .filter { (_, trg) -> trg in targets }
  .filter { (_, trg) -> trg != null }
  .map { (src, trg) -> src to trg!! }

data class NomisMovementId(val bookingId: Long, val sequence: Int) {
  override fun toString(): String = "${bookingId}_$sequence"
}
