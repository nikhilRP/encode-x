package org.encodedcc.encodex.cli

import org.apache.hadoop.mapreduce.Job
import org.apache.spark.SparkContext
import org.bdgenomics.adam.models.VariantContext
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.cli._
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.adam.models.ReferencePosition
import org.bdgenomics.adam.projections.{ Projection, VariantField, AlignmentRecordField, GenotypeField, NucleotideContigFragmentField, FeatureField }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Genotype, GenotypeAllele, NucleotideContigFragment }
import org.fusesource.scalate.TemplateEngine
import org.json4s._
import org.kohsuke.args4j.{ Argument, Option => Args4jOption }
import org.scalatra.json._
import org.scalatra.ScalatraServlet
import parquet.filter2.predicate.FilterPredicate
import parquet.filter2.dsl.Dsl._

object EncodeReads extends ADAMCommandCompanion {
  val commandName: String = "ENCODE X"
  val commandDescription: String = "an distributed data engine for ENCODE files"

  var sc: SparkContext = null
  var fileName: String = ""
  var readsExist: Boolean = false

  def apply(cmdLine: Array[String]): ADAMCommand = {
    new EncodeReads(Args4j[EncodeReadsArgs](cmdLine))
  }
}

class EncodeReadsArgs extends Args4jBase with ParquetArgs {

  @Args4jOption(required = false, name = "-encode_file", usage = "The encode file to search and retrieve")
  var fileName: String = null

  @Args4jOption(required = false, name = "-port", usage = "The port to bind to for visualization. The default is 8080.")
  var port: Int = 8080
}

class EncodeXServlet extends ScalatraServlet with JacksonJsonSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats

  get("/?") {
    redirect(url("search"))
  }

  get("/search") {
    <h1>Hello, you are on the search page</h1>
  }

}

class EncodeReads(protected val args: EncodeReadsArgs) extends ADAMSparkCommand[EncodeReadsArgs] {
  val companion: ADAMCommandCompanion = EncodeReads

  override def run(sc: SparkContext, job: Job): Unit = {
    EncodeReads.sc = sc

    val fileName = Option(args.fileName)
    fileName match {
      case Some(_) => {
        if (args.fileName.startsWith("ENCFF")) {
          EncodeReads.fileName = args.fileName
        } else {
          println("WARNING: Invalid ENCODE input file name")
        }
      }
      case None => println("WARNING: No file accession provided")
    }

    val server = new org.eclipse.jetty.server.Server(args.port)
    val handlers = new org.eclipse.jetty.server.handler.ContextHandlerCollection()

    server.setHandler(handlers)
    handlers.addHandler(new org.eclipse.jetty.webapp.WebAppContext("encode-x-cli/src/main/webapp", "/"))
    server.start()
    server.join()
  }
}