package deductions.runtime.services

import java.io.StringReader

import deductions.runtime.sparql_cache.SPARQLHelpers
import deductions.runtime.utils.RDFStoreLocalProvider
import deductions.runtime.utils.URIManagement
import deductions.runtime.core.HTTPrequest
import org.w3.banana.RDF
import org.w3.banana.io._

import scala.util.{Success, Try}

/**
 * A simple (partial) LDP implementation backed by SPARQL
 * http://www.w3.org/TR/ldp-primer/#creating-an-rdf-resource-post-an-rdf-resource-to-an-ldp-bc
 *
 * POST /alice/ HTTP/1.1
 * Host: example.org
 * Link: <http://www.w3.org/ns/ldp#Resource>; rel="type"
 * Slug: foaf
 * Content-Type: text/turtle
 *
 *
 * GET /alice/ HTTP/1.1
 * Host: example.org
 * Accept: text/turtle
 *
 * @author jmv
 */
trait LDP[Rdf <: RDF, DATASET]
    extends RDFStoreLocalProvider[Rdf, DATASET]
    with SPARQLHelpers[Rdf, DATASET]
    with RDFContentNegociationIO[Rdf, DATASET]
    with URIManagement {

  val turtleWriter: RDFWriter[Rdf, Try, Turtle]
  implicit val jsonldCompactedWriter: RDFWriter[Rdf, Try, JsonLdCompacted]
  val rdfXMLWriter: RDFWriter[Rdf, Try, RDFXML]

  implicit val turtleReader: RDFReader[Rdf, Try, Turtle]
  implicit val jsonldReader: RDFReader[Rdf, Try, JsonLd]

  import ops._

  val servicePrefix = "/ldp/"

  /**
   * for LDP GET
   *  @param uri relative URI received by LDP GET
   */
  def getTriples(uri: String, rawURI: String, accept: String, request: HTTPrequest): String = {
    println(s"LDP GET: (uri <$uri>, rawURI <$rawURI>, request $request)")
    val queryString = makeQueryString(uri, rawURI, request)
    println("LDP GET: queryString\n" + queryString)
    val r = rdfStore.r( dataset, {
      for {
        graph <- sparqlConstructQuery(queryString)
        s <- {
          val writer = getWriterFromMIME(accept)
          writer.asString(graph, uri)
        }
      } yield s
    })
    r.get.get
  }

  private def makeQueryString(uri: String, rawURI: String, request: HTTPrequest): String = {
		  println(s"makeQueryString rawURI $rawURI")
		  val absoluteURI = request . absoluteURL(rawURI)
      s"""
         |CONSTRUCT { <$absoluteURI> ?p ?o } WHERE {
         |  GRAPH ?G {
         |    <$absoluteURI> ?p ?o .
         |  }
         |}""".stripMargin
  }

  /** for LDP PUT or POST */
  def putTriples(uri: String, link: Option[String], contentType: Option[String],
    slug: Option[String],
    content: Option[String], request: HTTPrequest): Try[String] = {
    val putURI = request.absoluteURL( servicePrefix + uri +
      ( if( uri.endsWith("/") ) "" else "/" ) +
      slug.getOrElse("unnamed") )

    println(s"putTriples: content: ${content.get}")
    println(s"putTriples: contentType: ${contentType}")
    println(s"putTriples: slug: ${slug}")
    println(s"putTriples: PUT (graph) URI: ${putURI}")

    val r = rdfStore.rw( dataset, {
   // TODO content type negotiation is done elsewhere
   val reader =
          if (contentType.get.contains("text/turtle"))
            turtleReader
          else jsonldReader
      val resFor = for {
        graph <- reader.read(new StringReader(content.get), putURI)
        res <- {
          println("putTriples: graph: " + graph);
          rdfStore.removeGraph(dataset, URI(putURI))
        }
        res2 <- {
          println("putTriples: appendToGraph: URI= " + putURI);
          val res = rdfStore.appendToGraph(dataset, URI(putURI), graph)
          println("putTriples: after appendToGraph: URI= " + putURI)
          res
        }
      } yield res2
    })
    println("putTriples: transaction result " + r)
    Success(putURI)
  }
}
