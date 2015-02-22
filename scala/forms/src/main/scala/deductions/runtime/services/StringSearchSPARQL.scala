package deductions.runtime.services

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml.Elem

import org.w3.banana.RDF
import org.w3.banana.SparqlOpsModule
import org.w3.banana.TryW

import deductions.runtime.abstract_syntax.InstanceLabelsInference2
import deductions.runtime.dataset.RDFStoreLocalProvider
import deductions.runtime.html.Form2HTML

/** String Search with simple SPARQL */
trait StringSearchSPARQL[Rdf <: RDF, DATASET]
    extends InstanceLabelsInference2[Rdf] with SparqlOpsModule
    with RDFStoreLocalProvider[Rdf, DATASET] {

  import ops._
  import sparqlOps._

  def search(search: String, hrefPrefix: String = ""): Future[Elem] = {
    val uris = search_only(search)
    val elem = uris.map(
      u => displayResults(u.toIterable, hrefPrefix))
    elem
  }

  /** CAUTION: It is of particular importance to note that, unless stated otherwise, one should never use an iterator after calling a method on it. */
  private def displayResults(res0: Iterable[Rdf#Node], hrefPrefix: String) = {
    <p>{
      val res = res0.toSeq
      println(s"displayResults : ${res.mkString("\n")}")

      rdfStore.r(dataset, {
        implicit val graph: Rdf#Graph = allNamedGraph
        //        val all = ops.find( allNamedGraph, ANY, ANY, ANY)
        //        println( all.mkString("\n") )
        res.map(uri => {
          val uriString = uri.toString
          val blanknode = !isURI(uri)
          <div title=""><a href={ Form2HTML.createHyperlinkString(hrefPrefix, uriString, blanknode) }>
                          { instanceLabel(uri) }
                        </a><br/></div>
        })
      }).get
    }</p>
  }

  /**
   * NOTE: this stuff is pretty generic;
   *  just add these arguments :
   *  queryString:String, vars:Seq[String]
   *  transactional
   */
  private def search_only(search: String): Future[Iterator[Rdf#Node]] = {
    val queryString =
      s"""
         |SELECT DISTINCT ?thing WHERE {
         |  graph ?g {
         |    ?thing ?p ?string .
         |    FILTER regex( ?string, "$search", 'i')
         |  }
         |}""".stripMargin

    val transaction =
      rdfStore.r(dataset, {
        var i = 0
        val result = for {
          query <- parseSelect(queryString)
          solutions <- rdfStore.executeSelect(dataset, query, Map())
        } yield {
          solutions.toIterable.map {
            row =>
              i = i + 1
              println(s"search_only : $i $row")
              row("thing") getOrElse sys.error("")
          }
        }
        //        println( """result.get.mkString("")""" ) 
        //        println( result.get.mkString("") ) 
        result
      })
    val tryIteratorRdfNode = transaction.flatMap { identity }
    tryIteratorRdfNode.asFuture
  }

  def isURI(node: Rdf#Node) = ops.foldNode(node)(identity, x => None, x => None) != None

}