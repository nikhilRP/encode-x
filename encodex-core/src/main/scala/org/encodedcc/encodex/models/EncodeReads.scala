package org.encodedcc.encodex.models

import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Genotype, GenotypeAllele, NucleotideContigFragment }
import scala.collection.mutable
import scala.util.control.Breaks._
import org.apache.spark.rdd.MetricsContext._
import org.bdgenomics.utils.instrumentation.Metrics
import org.apache.spark.Logging

object TrackTimers extends Metrics {
  val FindAddTimer = timer("Find and Add to Track")
  val FindConflict = timer("Finds first nonconflicting track")
  val TrackAssignmentsTimer = timer("Generate track Assignments")
  val PlusRec = timer("Plus Rec")
  val MinusTrack = timer("Minus Track")
  val PlusTrack = timer("Plus Track")
  val LoopTimer = timer("Loop Timer")
  val GetIndexTimer = timer("Get Index Timer")
  val ConflictTimer = timer("Conflict Timer")
}
/**
 * A TrackedLayout is an assignment of values of some type T (which presumably are mappable to
 * a reference genome or other linear coordinate space) to 'tracks' -- that is, to integers,
 * with the guarantee that no two values assigned to the same track will overlap.
 *
 * This is the kind of data structure which is required for non-overlapping genome visualization.
 *
 * @tparam T the type of value which is to be tracked.
 */
trait TrackedLayout[T] {
  def numTracks: Int
  def trackAssignments: List[((ReferenceRegion, T), Int)]
}

object TrackedLayout {

  def overlaps[T](rec1: (ReferenceRegion, T), rec2: (ReferenceRegion, T)): Boolean = {
    val ref1 = rec1._1
    val ref2 = rec2._1
    ref1.overlaps(ref2)
  }
}

/**
 * An implementation of TrackedLayout which takes a sequence of tuples of Reference Region and an input type,
 * and lays them out <i>in order</i> (i.e. from first-to-last).
 * Tracks are laid out by finding the first track in a buffer that does not conflict with
 * an input ReferenceRegion. After a ReferenceRegion is added to a track, the track is
 * put at the end of the buffer in an effort to make subsequent searches faster.
 *
 * @param values The set of values (i.e. reads, variants) to lay out in tracks
 * @tparam T the type of value which is to be tracked.
 */
class OrderedTrackedLayout[T](values: Traversable[(ReferenceRegion, T)]) extends TrackedLayout[T] with Logging {
  private var trackBuilder = new mutable.ListBuffer[Track]()
  val sequence = values.toSeq
  log.info("Number of values: " + values.size)

  TrackTimers.FindAddTimer.time {
    sequence match {
      case a: Seq[(ReferenceRegion, AlignmentRecord)] => a.foreach(findAndAddToTrack)
      case f: Seq[(ReferenceRegion, Feature)]         => f.foreach(findAndAddToTrack)
      case g: Seq[(ReferenceRegion, Genotype)]        => addVariantsToTrack(g.groupBy(_._1))
    }
  }

  trackBuilder = trackBuilder.filter(_.records.nonEmpty)

  val numTracks = trackBuilder.size
  log.info("Number of tracks: " + numTracks)
  val trackAssignments: List[((ReferenceRegion, T), Int)] = TrackTimers.TrackAssignmentsTimer.time {
    val zippedWithIndex = trackBuilder.toList.zip(0 to numTracks)
    val flatMapped: List[((ReferenceRegion, T), Int)] = zippedWithIndex.flatMap {
      case (track: Track, idx: Int) => track.records.map(_ -> idx)
    }
    flatMapped
  }

  private def addVariantsToTrack(sites: Map[ReferenceRegion, Seq[(ReferenceRegion, T)]]) {
    for (site <- sites) {
      val trackBuilderIterator = trackBuilder.toIterator
      for (rec <- site._2) {
        if (!trackBuilderIterator.hasNext) {
          addTrack(new Track(rec))
        } else {
          val track = trackBuilderIterator.next
          track += rec
        }
      }
    }
  }

  private def findAndAddToTrack(rec: (ReferenceRegion, T)) {
    val reg = rec._1
    if (reg != null) {
      val track: Option[Track] = TrackTimers.FindConflict.time {
        trackBuilder.find(track => !track.conflicts(rec))
      }
      track.map(trackval => {
        trackval += rec
        trackBuilder -= trackval
        trackBuilder += trackval
      }).getOrElse(addTrack(new Track(rec)))
    }
  }

  private def addTrack(t: Track): Track = {
    trackBuilder += t
    t
  }

  class Track(val initial: (ReferenceRegion, T)) {

    val records = new mutable.ListBuffer[(ReferenceRegion, T)]()
    records += initial

    def +=(rec: (ReferenceRegion, T)): Track = {
      records += rec
      this
    }

    def conflicts(rec: (ReferenceRegion, T)): Boolean =
      records.exists(r => TrackedLayout.overlaps(r, rec))
  }

}
