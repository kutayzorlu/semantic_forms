package deductions.runtime.services

import org.w3.banana.SparqlEngine
import org.w3.banana.RDF
import org.w3.banana.RDFOps
import org.w3.banana.SparqlOps
import org.w3.banana.RDFStore
import scala.xml.Elem
import scala.concurrent._
import scala.concurrent.util._
import scala.concurrent.ExecutionContext.Implicits.global
import deductions.runtime.html.Form2HTML
import scala.concurrent.duration._
import org.apache.log4j.Logger


class BrowsableGraph [Rdf <: RDF](store: RDFStore[Rdf])(
  implicit
  ops: RDFOps[Rdf],
  sparqlOps: SparqlOps[Rdf]) {

  import ops._
  import sparqlOps._

  val sparqlEngine = SparqlEngine[Rdf](store)

  def focusOnURI( uri:String ) {
    
  }
    private def search_only(search: String) = {
   val queryString =
      s"""
         |CONSTRUCT { ?thing ?p ?o . } WHERE {
         |  graph ?g {
         | UNION  # TODO <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
         |    ?thing ?p ?o .
         |    ?s ?p1 ?thing .
         |  }
         |}""".stripMargin
    val query = ConstructQuery(queryString)
    val es = sparqlEngine.executeConstruct(query)
    es
  }
    
  
    private def search_only2(search: String) = {
   val queryString =
      s"""
         |SELECT DISTINCT ?thing WHERE {
         |  graph ?g {
         |    ?thing ?p ?string .
         |    FILTER regex( ?string, "$search", 'i')
         |  }
         |}""".stripMargin
    val query = SelectQuery(queryString)
    val es = sparqlEngine.executeSelect(query)
    val uris: Future[Iterable[Rdf#Node]] = es.
      map(_.toIterable.map {
        row => row("thing") getOrElse sys.error("")
      })
    uris
  }
}