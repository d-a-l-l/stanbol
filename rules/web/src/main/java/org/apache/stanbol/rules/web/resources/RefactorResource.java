package org.apache.stanbol.rules.web.resources;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

import java.io.InputStream;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.clerezza.rdf.core.access.TcManager;
import org.apache.stanbol.commons.web.base.ContextHelper;
import org.apache.stanbol.commons.web.base.format.KRFormat;
import org.apache.stanbol.commons.web.base.resource.BaseStanbolResource;
import org.apache.stanbol.ontologymanager.ontonet.api.ONManager;
import org.apache.stanbol.rules.base.api.NoSuchRecipeException;
import org.apache.stanbol.rules.base.api.Recipe;
import org.apache.stanbol.rules.base.api.util.RuleList;
import org.apache.stanbol.rules.manager.KB;
import org.apache.stanbol.rules.manager.changes.RecipeImpl;
import org.apache.stanbol.rules.manager.parse.RuleParserImpl;
import org.apache.stanbol.rules.refactor.api.Refactorer;
import org.apache.stanbol.rules.refactor.api.RefactoringException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.view.ImplicitProduces;
import com.sun.jersey.multipart.FormDataParam;

/**
 * 
 * @author andrea.nuzzolese
 * 
 */

@Path("/refactor")
@ImplicitProduces(MediaType.TEXT_HTML + ";qs=2")
public class RefactorResource extends BaseStanbolResource {

    private Logger log = LoggerFactory.getLogger(getClass());

    protected ONManager onManager;
    protected Refactorer refactorer;
    protected TcManager tcManager;

    public RefactorResource(@Context ServletContext servletContext) {
    	refactorer = (Refactorer) ContextHelper.getServiceFromContext(Refactorer.class, servletContext);
    	onManager = (ONManager) ContextHelper.getServiceFromContext(ONManager.class, servletContext);
        tcManager = (TcManager) ContextHelper.getServiceFromContext(TcManager.class, servletContext);
        if (refactorer == null) {
            throw new IllegalStateException("SemionRefactorer missing in ServletContext");
        }

    }

    /**
     * The apply mode allows the client to compose a recipe, by mean of string containg the rules, and apply
     * it "on the fly" to the graph in input.
     * 
     * @param recipe
     *            String
     * @param input
     *            InputStream
     * @return a Response containing the transformed graph
     */
    @POST
    @Path("/apply")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(value = {KRFormat.TURTLE, KRFormat.FUNCTIONAL_OWL, KRFormat.MANCHESTER_OWL, KRFormat.RDF_XML,
                       KRFormat.OWL_XML, KRFormat.RDF_JSON})
    public Response applyRefactoring(@FormDataParam("recipe") String recipe, @FormDataParam("input") InputStream input) {

        // Refactorer semionRefactorer = semionManager.getRegisteredRefactorer();

        KB kb = RuleParserImpl.parse(recipe);

        if (kb == null) return Response.status(NOT_FOUND).build();

        RuleList ruleList = kb.getkReSRuleList();
        if (ruleList == null) return Response.status(NOT_FOUND).build();
        Recipe actualRecipe = new RecipeImpl(null, null, ruleList);

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology inputOntology;
        try {
            inputOntology = manager.loadOntologyFromOntologyDocument(input);
            OWLOntology outputOntology;
            try {
                outputOntology = refactorer.ontologyRefactoring(inputOntology, actualRecipe);
            } catch (RefactoringException e) {
                // refactoring exceptions are re-thrown
                throw new WebApplicationException(e, INTERNAL_SERVER_ERROR);
            } catch (NoSuchRecipeException e) {
                // missing recipes result in a status 404
                return Response.status(NOT_FOUND).build();
            }
            return Response.ok(outputOntology).build();
        } catch (OWLOntologyCreationException e) {
            throw new WebApplicationException(e, INTERNAL_SERVER_ERROR);
        }

    }

    public String getNamespace() {
        return onManager.getKReSNamespace();
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(value = {KRFormat.TURTLE, KRFormat.FUNCTIONAL_OWL, KRFormat.MANCHESTER_OWL, KRFormat.RDF_XML,
                       KRFormat.OWL_XML, KRFormat.RDF_JSON})
    public Response performRefactoring(@FormDataParam("recipe") String recipe,
                                       @FormDataParam("input") InputStream input) {

        // Refactorer semionRefactorer = semionManager.getRegisteredRefactorer();

        IRI recipeIRI = IRI.create(recipe);

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology inputOntology;
        try {
            inputOntology = manager.loadOntologyFromOntologyDocument(input);

            OWLOntology outputOntology;
            try {
                outputOntology = refactorer.ontologyRefactoring(inputOntology, recipeIRI);
            } catch (RefactoringException e) {
                // refactoring exceptions are re-thrown
                throw new WebApplicationException(e, INTERNAL_SERVER_ERROR);
            } catch (NoSuchRecipeException e) {
                // missing recipes result in a status 404
                return Response.status(NOT_FOUND).build();
            }
            return Response.ok(outputOntology).build();
        } catch (OWLOntologyCreationException e) {
            throw new WebApplicationException(e, INTERNAL_SERVER_ERROR);
        }

    }

    @GET
    public Response performRefactoringLazyCreateGraph(@QueryParam("recipe") String recipe,
                                                      @QueryParam("input-graph") String inputGraph,
                                                      @QueryParam("output-graph") String outputGraph) {

        log.info("recipe: {}", recipe);
        log.info("input-graph: {}", inputGraph);
        log.info("output-graph: {}", outputGraph);
        IRI recipeIRI = IRI.create(recipe);
        IRI inputGraphIRI = IRI.create(inputGraph);
        IRI outputGraphIRI = IRI.create(outputGraph);

        // Refactorer semionRefactorer = semionManager.getRegisteredRefactorer();

        try {
            refactorer.ontologyRefactoring(outputGraphIRI, inputGraphIRI, recipeIRI);
            return Response.ok().build();
        } catch (RefactoringException e) {
            // refactoring exceptions are re-thrown
            throw new WebApplicationException(e, INTERNAL_SERVER_ERROR);
        } catch (NoSuchRecipeException e) {
            // missing recipes result in a status 404
            return Response.status(NOT_FOUND).build();
        }

    }

}
