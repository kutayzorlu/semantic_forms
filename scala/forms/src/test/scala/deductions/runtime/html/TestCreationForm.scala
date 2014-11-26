package deductions.runtime.html
import org.scalatest.FunSuite
import deductions.runtime.jena.RDFStoreObject
import org.w3.banana.PrefixBuilder
import org.w3.banana.GraphStore
import org.w3.banana.RDF
import org.w3.banana._
import org.w3.banana.syntax._
import org.w3.banana.diesel._
import deductions.runtime.utils.Fil‍eUtils
import java.nio.file.Files
import java.nio.file.Paths

class TestCreationForm extends FunSuite with CreationForm {
  val form = new PrefixBuilder[Rdf]("form", "http://deductions-software.com/ontologies/forms.owl.ttl#" )
  val foaf = FOAFPrefix[Rdf]
        
  test("display form") {
    Fil‍eUtils.deleteLocalSPARL()
    val uri = // 
//      "http://usefulinc.com/ns/doap#Project"
     // "http://xmlns.com/foaf/0.1/Organization"
        "http://xmlns.com/foaf/0.1/Person"
    val store =  RDFStoreObject.store
    retrieveURI( Ops.makeUri(uri), store )
    val form = create(uri, lang="fr") 
    Files.write(Paths.get("example.creation.form.html"), form.toString().getBytes );
    assert ( form . toString() . contains("homepage") )
  }

}