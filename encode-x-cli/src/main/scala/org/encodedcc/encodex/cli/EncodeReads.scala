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
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Genotype, GenotypeAllele, NucleotideContigFragment }
import org.bdgenomics.utils.instrumentation.Metrics
import org.fusesource.scalate.TemplateEngine
import org.kohsuke.args4j.{ Argument, Option => Args4jOption }
import org.scalatra.json._
import org.scalatra.ScalatraServlet
import org.json4s.{ DefaultFormats, Formats }

object EncodeTimers extends Metrics {
  //HTTP requests
  val ReadsRequest = timer("GET reads")

  //RDD operations
  var LoadParquetFile = timer("Loading from Parquet")

  //Generating Json
  val MakingTrack = timer("Making Track")
}

object EncodeReads extends BDGCommandCompanion with Logging {
  val commandName: String = "ENCODE X"
  val commandDescription: String = "an distributed data engine for ENCODE files"

  var sc: SparkContext = null
  var server: org.eclipse.jetty.server.Server = null

  def apply(cmdLine: Array[String]): BDGCommand = {
    new EncodeReads(Args4j[EncodeReadsArgs](cmdLine))
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

class EncodeReadsArgs extends Args4jBase with ParquetArgs {
  @Args4jOption(required = false, name = "-port", usage = "The port to bind to for visualization. The default is 8080.")
  var port: Int = 8080
}

class EncodeXServlet extends ScalatraServlet with JacksonJsonSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json")
  }

  get("/?") {
    redirect(url("search"))
  }

  get("/search") {
    "Welcome to ENCODE BAM file alignment retrivel engine"
  }

  get("/search/:file") {
    EncodeTimers.ReadsRequest.time {
      var fileName: String = params.get("file").toString
      if (params("ref") != null) {
        val data = params("ref").split("-")
        val reference = data(0).split(":")(0)
        val start = data(0).split(":")(1).replace(",", "").toLong
        var end: Long = start
        if (data(1) != null) {
          end = data(1).replace(",", "").toLong
        }
      }
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
    handlers.addHandler(new org.eclipse.jetty.webapp.WebAppContext("encode-x-cli/src/main/webapp", "/"))
    EncodeReads.server.start()
    EncodeReads.server.join()
  }
}
