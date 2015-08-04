package org.encodedcc.encodex.cli

import org.apache.hadoop.mapreduce.Job
import org.apache.spark.{ Logging, SparkContext }
import org.bdgenomics.adam.models.VariantContext
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.MetricsContext._
import org.bdgenomics.utils.cli._
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.adam.models.ReferencePosition
import org.bdgenomics.adam.projections.{ Projection, VariantField, AlignmentRecordField, GenotypeField, NucleotideContigFragmentField, FeatureField }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.encodedcc.encodex.models.OrderedTrackedLayout
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Genotype, GenotypeAllele, NucleotideContigFragment }
import org.bdgenomics.utils.instrumentation.Metrics
import org.fusesource.scalate.TemplateEngine
import org.json4s._
import org.kohsuke.args4j.{ Argument, Option => Args4jOption }
import net.liftweb.json.Serialization.write
import org.scalatra.ScalatraServlet
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.parquet.filter2.dsl.Dsl._

object EncodeXTimers extends Metrics {
  //HTTP requests
  val ReadsRequest = timer("GET reads")

  //RDD operations
  var LoadParquetFile = timer("Loading from Parquet")
  val ReadsRDDTimer = timer("RDD Reads operations")

  //Generating JSON
  val MakingTrack = timer("Making Track")
  val DoingCollect = timer("Doing Collect")
  val PrintTrackJsonTimer = timer("JSON printTrackJson")
}

object EncodeReads extends BDGCommandCompanion with Logging {

  val commandName: String = "encode-x"
  val commandDescription: String = "distributed engine for ENCODE data"
  val filesLocation: String = "/user/nikhilrp/encoded-data/mm10/"

  var sc: SparkContext = null
  var server: org.eclipse.jetty.server.Server = null

  def apply(cmdLine: Array[String]): BDGCommand = {
    new EncodeReads(Args4j[EncodeReadsArgs](cmdLine))
  }

  //Prepares reads information in json format
  def printTrackJson(layout: OrderedTrackedLayout[AlignmentRecord]): List[TrackJson] = EncodeXTimers.PrintTrackJsonTimer.time {
    var tracks = new scala.collection.mutable.ListBuffer[TrackJson]
    for (rec <- layout.trackAssignments) {
      val aRec = rec._1._2.asInstanceOf[AlignmentRecord]
      tracks += new TrackJson(aRec.getReadName, aRec.getStart, aRec.getEnd, rec._2)
    }
    tracks.toList
  }

  //Correctly shuts down the server
  def quit() {
    val thread = new Thread {
      override def run {
        try {
          log.info("Shutting down the server")
          println("Shutting down the server")
          server.stop();
          log.info("Server has stopped")
          println("Server has stopped")
        } catch {
          case e: Exception => {
            log.info("Error when stopping Jetty server: " + e.getMessage(), e)
            println("Error when stopping Jetty server: " + e.getMessage(), e)
          }
        }
      }
    }
    thread.start()
  }

}

case class TrackJson(readName: String, start: Long, end: Long, track: Long)
case class VariationJson(contigName: String, alleles: String, start: Long, end: Long, track: Long)
case class FreqJson(base: Long, freq: Long)
case class FeatureJson(featureId: String, featureType: String, start: Long, end: Long, track: Long)
case class ReferenceJson(reference: String)

class EncodeReadsArgs extends Args4jBase with ParquetArgs {

  @Args4jOption(required = false, name = "-port", usage = "The port to bind to for visualization. The default is 8080.")
  var port: Int = 8080
}

class EncodeXServlet extends ScalatraServlet {
  implicit val formats = net.liftweb.json.DefaultFormats

  get("/?") {
    redirect("/reads")
  }

  get("/quit") {
    EncodeReads.quit()
  }

  get("/reads") {
    contentType = "json"
    write("Please enter file name eg: /reads/ENCFF984VBZ")
  }

  get("/reads/:file") {
    EncodeXTimers.ReadsRequest.time {
      contentType = "json"
      var start: Long = params.getOrElse("start", "0").toLong;
      var end: Long = params.getOrElse("end", "100").toLong;
      var refName: String = params.getOrElse("ref", "chr1");
      var fileName: String = params.getOrElse("file", "ENCFF984VBZ");
      var pred: FilterPredicate = ((LongColumn("start") < end) && (LongColumn("end") > start))
      var proj = Projection(AlignmentRecordField.contig, AlignmentRecordField.readName, AlignmentRecordField.start, AlignmentRecordField.end)
      var readsRDD: RDD[AlignmentRecord] = EncodeXTimers.LoadParquetFile.time {
        EncodeReads.sc.loadParquetAlignments(EncodeReads.filesLocation + refName + "/" + fileName + ".adam",
          predicate = Some(pred),
          projection = Some(proj))
      }
      var trackinput: RDD[(ReferenceRegion, AlignmentRecord)] = readsRDD.keyBy(ReferenceRegion(_))
      var collected = EncodeXTimers.DoingCollect.time {
        trackinput.collect()
      }
      var filteredLayout = EncodeXTimers.MakingTrack.time {
        new OrderedTrackedLayout(collected)
      }
      write(EncodeReads.printTrackJson(filteredLayout))
    }
  }
}

class EncodeReads(protected val args: EncodeReadsArgs) extends BDGSparkCommand[EncodeReadsArgs] with Logging {
  val companion: BDGCommandCompanion = EncodeReads

  override def run(sc: SparkContext): Unit = {
    EncodeReads.sc = sc

    EncodeReads.server = new org.eclipse.jetty.server.Server(args.port)
    val handlers = new org.eclipse.jetty.server.handler.ContextHandlerCollection()
    EncodeReads.server.setHandler(handlers)
    handlers.addHandler(new org.eclipse.jetty.webapp.WebAppContext("encodex-cli/src/main/webapp", "/"))
    EncodeReads.server.start()
    println("View the visualization at: " + args.port)
    println("Quit at: /quit")
    EncodeReads.server.join()
  }
}