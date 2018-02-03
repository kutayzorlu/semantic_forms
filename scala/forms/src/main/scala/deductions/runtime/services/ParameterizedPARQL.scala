package deductions.runtime.services

import deductions.runtime.abstract_syntax.{InstanceLabelsInferenceMemory, PreferredLanguageLiteral}
import deductions.runtime.html.Form2HTMLDisplay
import deductions.runtime.sparql_cache.SPARQLHelpers
import deductions.runtime.utils.RDFStoreLocalProvider
import deductions.runtime.views.ResultsDisplay
import org.w3.banana.{RDF, TryW}

import scala.concurrent.Future
import scala.xml.{Elem, NodeSeq, Text}
import scala.xml.Unparsed
import deductions.runtime.core.HTTPrequest


trait SPARQLQueryMaker[Rdf <: RDF] {
  // TODO : search: String*
  def makeQueryString(search: String*): String
  def variables = Seq("thing")
    /** overridable function for adding columns in response */
  def columnsForURI( node: Rdf#Node, label: String): NodeSeq = Text("")

  /** prepare Search String: trim, and replace ' with \' */
  def prepareSearchString(search: String) = {
    search.trim().replace("'", """\'""")
  }
}

/**
 * Generic SPARQL SELECT Search with single parameter,
 * and single return URI value,
 *  and showing in HTML a column of hyperlinked results with instance Labels
 */
abstract trait ParameterizedSPARQL[Rdf <: RDF, DATASET]
    extends RDFStoreLocalProvider[Rdf, DATASET]
    with InstanceLabelsInferenceMemory[Rdf, DATASET]
    with PreferredLanguageLiteral[Rdf]
    with SPARQLHelpers[Rdf, DATASET]
    with Form2HTMLDisplay[Rdf#Node, Rdf#URI]
    with ResultsDisplay[Rdf, DATASET] {

  import config._

  /**
   * Generic SPARQL SELECT with single result columns (must be named "thing"),
   * generate a column of HTML hyperlinks for given search string;
   * search and display results as an XHTML element
   * transactional
   * @param hrefPrefix URL prefix for creating hyperlinks ((URI of each query result is concatenated)
   *
   * TODO
   * - displayResults should be a function argument
   * - search2 is very similar!
   */
  def search(
    hrefPrefix:  String,
    lang:        String,
    search:      Seq[String],
    variables:   Seq[String] = Seq("?thing"),
    httpRequest: HTTPrequest = HTTPrequest())(
    implicit
    queryMaker: SPARQLQueryMaker[Rdf]): Future[NodeSeq] = {
    println(s"search($search) 1: starting TRANSACTION for dataset $dataset")
    val elem0 = rdfStore.rw(dataset, {
      val uris = search_onlyNT(search, variables, httpRequest)
      println(s"\tsearch(): URI's size ${uris.size}")
      val graph: Rdf#Graph = allNamedGraph
      val elems = Seq(
        <button value="Sort" id="sort">
          Sort
        </button>,
        sortJSscript,
        <div id="container" class={ css.tableCSSClasses.formRootCSSClass }> {
          css.localCSS ++
            uris.map {
              u =>
                // println(s"\tsearch(): URI row $u")
                displayResults(u, hrefPrefix, lang, graph, false)
            }
        }</div>)
      elems
    })
    println(s"search: leaving TRANSACTION for dataset $dataset")
    val elem = elem0.get
    Future.successful(elem)
  }

  private val sortJSscript =
    <script> {
      Unparsed("""
        //// console.log("divs " + JSON.stringify($divs) );
        $('#sort').on('click', function () {
              var $divs = $("div.sf-triple-block")
              $divs.sort(function (a, b) {
                  var atext = $(a).find(".sf-rdf-link").text().trim().toLowerCase().replace("_", " ")
                  var btext = $(b).find(".sf-rdf-link").text().trim().toLowerCase().replace("_", " ")
                  return atext.localeCompare(btext)
              })
//            for (e of $divs) {
//                console.log( '"' + $(e).find(".sf-rdf-link").text().trim().toLowerCase().replace("_", " ") + '"' )
//              }
              let wrapper = $('#container')
              wrapper.empty()
              for (e of $divs) {
                $(e).appendTo(wrapper)
              }
            })
      """)
    } </script>

  /**
   * Generic SPARQL SELECT Search with multiple result columns;
   * search and display results as an XHTML element;
   * generate rows of HTML hyperlinks for given search string;
   * transactional
   * @param hrefPrefix URL prefix for creating hyperlinks ((URI of each query result is concatenated)
   */
  def search2(search: String, hrefPrefix: String = config.hrefDisplayPrefix,
              lang: String = "")(implicit queryMaker: SPARQLQueryMaker[Rdf]): Elem = {
    val uris = search_only2(search)
    val elem0 =
      rdfStore.rw(dataset, {
        val graph: Rdf#Graph = allNamedGraph
        <div class={ css.tableCSSClasses.formRootCSSClass }>
        <div>Size: { uris.size }</div>
        {
          css.localCSS ++
            uris.map {
              // create table like HTML
              u =>
                //            println(s"**** search2 u $u")
                displayResults(u.toIterable, hrefPrefix, lang, graph)
            }
        }</div>
      })
    val elem = elem0.get
    elem
  }

  /**
   * TRANSACTIONAL
   *
   * CAUTION: It is of particular importance to note that
   * one should never use an Iterator after calling a method on it;
   * cf http://stackoverflow.com/questions/18420995/scala-iterator-one-should-never-use-an-iterator-after-calling-a-method-on-it
   */
  private def search_only(search: String*)(implicit queryMaker: SPARQLQueryMaker[Rdf]): 
	  Future[List[Seq[Rdf#Node]]]
			  // : Future[Iterator[Rdf#Node]]
		= {
    println(s"search 2: starting TRANSACTION for dataset $dataset")
    val transaction =
      rdfStore.r(dataset, {
        search_onlyNT(search)
      })
    val tryIteratorRdfNode = transaction // .flatMap { identity }
    println(s"after search_only(search tryIteratorRdfNode $tryIteratorRdfNode")
    tryIteratorRdfNode.asFuture
  }
  
  private def search_onlyNT(search: Seq[String], variables: Seq[String] = Seq("?thing"),
      httpRequest: HTTPrequest = HTTPrequest())
  (implicit queryMaker: SPARQLQueryMaker[Rdf] ) = {
    val queryString: String = {
      val rawQueryString = queryMaker.makeQueryString(search :_* )
      addLimit(rawQueryString, httpRequest)
    }
//    logger.debug(
        println( s"search_onlyNT(search='$search') \n$queryString \n\tdataset Class ${dataset.getClass().getName}" )
    // NOTE: if class is specified in request, then ?CLASS is not in results, and vice-versa
    println( s"search_onlyNT: search: ($search) : SPARQL variables $variables" )
    sparqlSelectQueryVariablesNT(queryString, variables )
  }

  /** with result variables specified; transactional */
  private def search_only2(search: String)
  (implicit queryMaker: SPARQLQueryMaker[Rdf] ): List[Seq[Rdf#Node]] = {
    val dsg = dataset.asInstanceOf[org.apache.jena.sparql.core.DatasetImpl].asDatasetGraph()
    println(s">>>> dsg class : ${dsg.getClass}")
    
    val queryString = queryMaker.makeQueryString(search)
	  println( s"search_only2( search $search" )
    sparqlSelectQueryVariables(queryString, queryMaker.variables )
  }

  private def addLimit(rawQueryString: String, httpRequest: HTTPrequest) = {
    val queryWithLimit = httpRequest.getHTTPparameterValue("limit") match {
      case Some(limit) => s"$rawQueryString LIMIT $limit"
      case None => rawQueryString
    }
    httpRequest.getHTTPparameterValue("offset") match {
      case Some(offset) => s"$queryWithLimit OFFSET $offset"
      case None => queryWithLimit
    }
  }

}
