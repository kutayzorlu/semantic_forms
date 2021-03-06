package deductions.runtime.jena.lucene

import java.io.File
import java.nio.file.Paths

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

import org.apache.commons.io.FileUtils
import org.apache.jena.graph.Factory
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.Node_Literal
import org.apache.jena.graph.Node_URI
import org.apache.jena.query.Dataset
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.ReadWrite
import org.apache.jena.query.ResultSetFormatter
import org.apache.jena.query.text.DatasetGraphText
import org.apache.jena.query.text.EntityDefinition
import org.apache.jena.query.text.TextDatasetFactory
import org.apache.jena.query.text.TextIndex
import org.apache.jena.query.text.TextIndexConfig
import org.apache.jena.query.text.TextIndexException
import org.apache.jena.query.text.TextIndexLucene
import org.apache.jena.tdb.TDBFactory
import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.store.NIOFSDirectory

/**
 * Test Lucene Text Index, no Banana dependency !!!
 *
 *  - initialize TDB with Lucene
 *  - populate TDB with 2 triples
 *  - dump Lucene
 *  - sparql Query
 *  - dump TDB
 */
object TestTextIndex2 extends App {

  val rdfs = "http://www.w3.org/2000/01/rdf-schema#"
  val foaf = "http://xmlns.com/foaf/0.1/"

  val pred1 = makeUri(rdfs + "label")
  val pred2 = makeUri(foaf + "givenName")

  val directory = "Dataset1"
  val LUCENEtest = "LUCENEtest2"

//  FileUtils.deleteDirectory(new File(directory))
//  FileUtils.deleteDirectory(new File(LUCENEtest))

  println(s"""TDB directory = "$directory" , LUCENE directory = "$LUCENEtest" """)

  val dataset0 = TDBFactory.createDataset(directory)
  val dataset = configureLuceneIndex(dataset0)

  populateTDB()

  getTextIndex() match {
    case Some(textIndex) =>
      println(s"dump(textIndex=$textIndex)")
      println("textIndex.getDocDef.fields " + textIndex.getDocDef.fields())
      dump(textIndex)
    case _ =>
  }

  sparqlQuery(query2)
  sparqlQuery(query)
  dataset.close()

  println(s"""tdb.tdbdump (after dataset.close() )""")
  tdb.tdbdump.main("--loc", directory)

  ////

  def populateTDB() = {
    try {
      dataset.begin(ReadWrite.WRITE)
      val s = makeUri("test:/test1");
      {
        val g2 = makeUri("test:/test-extra-data")
        val graph = Factory.createDefaultGraph
        val o = makeLiteral("test-extra-data")
        val tr = makeTriple(s, pred1, o)
        graph.add(tr)
        dataset.asDatasetGraph().addGraph(g2, graph)
        println(s">>>> graph added: $g2 $graph")
      }
      {
        val o = makeLiteral("test1")
        val tr = makeTriple(s, pred1, o)
        val tr2 = makeTriple(s, pred2, o)
        val graph = Factory.createDefaultGraph
        graph.add(tr)
        graph.add(tr2)
        val g = makeUri("test:/test1")
        dataset.asDatasetGraph().addGraph(g, graph)
        println(s">>>> graph added: $g $graph")
      }
      dataset.commit()
    } catch {
      case t: Throwable =>
        System.err.println("!!!! error " + t.getLocalizedMessage)
    } finally dataset.end()

    println(s"After transaction")
  }

  def makeUri(iriStr: String) = { NodeFactory.createURI(iriStr).asInstanceOf[Node_URI] }
  def makeLiteral(lexicalForm: String) =
    NodeFactory.createLiteral(lexicalForm, null, null).asInstanceOf[Node_Literal]
  def makeTriple(s: Node, p: Node_URI, o: Node): org.apache.jena.graph.Triple =
    org.apache.jena.graph.Triple.create(s, p, o)

  /** pasted from jena.textindexdump */
  def dump(textIndex: TextIndexLucene) = {
    try {
      val directory = textIndex.getDirectory()
      val analyzer = textIndex.getQueryAnalyzer()
      val indexReader = DirectoryReader.open(directory)
      val indexSearcher = new IndexSearcher(indexReader)
      val queryParser = new QueryParser(
        textIndex.getDocDef().getPrimaryField(), analyzer)
      val query = queryParser.parse("*:*")
      val sDocs = indexSearcher.search(query, 1000).scoreDocs
      printScoreDocs(sDocs, indexSearcher)

      println("""search "test1" """)
      val query1 = queryParser.parse("test1")
      val res = indexSearcher.search(query1, 1000).scoreDocs
      printScoreDocs(res, indexSearcher)

    } catch {
      case ex: Throwable =>
        ex.printStackTrace()
        throw new TextIndexException(ex);
    }
  }

  /** configure Lucene Index for Jena */
  def configureLuceneIndex(dataset: Dataset): Dataset = {
    println(
      s"configureLuceneIndex: rdfIndexing getPredicates ${rdfIndexing.getPredicates("text")}")
    val directory = new NIOFSDirectory(Paths.get(LUCENEtest))
    val textIndex: TextIndex = TextDatasetFactory.createLuceneIndex(
      directory, new TextIndexConfig(rdfIndexing))
    println("rdfIndex: " + rdfIndexing.toString())
    TextDatasetFactory.create(dataset, textIndex, true)
  }

  def getTextIndex(): Option[TextIndexLucene] = {
    val dsg = dataset.asDatasetGraph()
    println(s"dsg class : ${dsg.getClass}")
    dsg match {
      case dsgText: DatasetGraphText =>
        println(s"case dsgText : ${dsg.getClass}")
        val textIndex = dsgText.getTextIndex
        println(s"textIndex class : ${textIndex.getClass}")
        textIndex match {
          case textIndex: TextIndexLucene => Some(textIndex)
          case _                          => None
        }
      case _ =>
        println(s"!!!!!!!!!!! DatasetGraphText not found!")
        None
    }
  }

  /** cf trait InstanceLabelsInference */
  lazy val rdfIndexing: EntityDefinition = {
    val entMap = new EntityDefinition("uri", "text", rdfs + "label")
    entMap.setLangField("lang")
    entMap.setUidField("uid")
    entMap.setGraphField("graph")

    entMap.set("text", makeUri(foaf + "givenName"))
    entMap.set("text", makeUri(foaf + "familyName"))
    entMap.set("text", makeUri(foaf + "firstName"))
    entMap.set("text", makeUri(foaf + "lastName"))
    entMap.set("text", makeUri(foaf + "name"))
    entMap.set("text", makeUri(rdfs + "comment"))
    entMap
  }

  def printScoreDocs(sDocs: Array[ScoreDoc], indexSearcher: IndexSearcher) = {
    for (sd <- sDocs) {
      println("Doc: " + sd.doc);
      val doc: Document = indexSearcher.doc(sd.doc);
      // Don't forget that many fields aren't stored, just indexed.
      var i = 0
      for (f <- doc.asScala) {
        i = i + 1
        println(s"  $i $f");
        println("  " + f.name() + " = " + f.stringValue())
        //              f.fieldType() )
      }
    }
  }

  lazy val query = """
    PREFIX text: <http://jena.apache.org/text#> 
    SELECT * WHERE {
      graph ?g {
        ?thing text:query 'test1' .
        ?thing ?p ?o .
      }
    }
    """

  lazy val query2 = """
    PREFIX text: <http://jena.apache.org/text#> 
    SELECT * WHERE {
      graph ?g {
        ?thing ?p ?o .
      }
    }
    """
    
  def sparqlQuery(query: String) = {
		println(s"sparql Query $query")
    dataset.begin(ReadWrite.READ)
    try {
      val qExec = QueryExecutionFactory.create(query, dataset)
      val rs = qExec.execSelect()
      ResultSetFormatter.out(rs)
      dataset.end()
    } catch {
      case ex: Throwable =>
        ex.printStackTrace()
        dataset.end()
    }
  }
}
