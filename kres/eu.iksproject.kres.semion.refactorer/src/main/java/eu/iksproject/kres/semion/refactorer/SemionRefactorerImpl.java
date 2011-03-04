package eu.iksproject.kres.semion.refactorer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Dictionary;

import org.apache.clerezza.rdf.core.Graph;
import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.access.TcManager;
import org.apache.clerezza.rdf.core.access.WeightedTcProvider;
import org.apache.clerezza.rdf.core.impl.SimpleMGraph;
import org.apache.clerezza.rdf.core.serializedform.Serializer;
import org.apache.clerezza.rdf.core.sparql.NoQueryEngineException;
import org.apache.clerezza.rdf.core.sparql.ParseException;
import org.apache.clerezza.rdf.core.sparql.QueryParser;
import org.apache.clerezza.rdf.core.sparql.query.ConstructQuery;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.RDFXMLOntologyFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.util.OWLOntologyMerger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.Syntax;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.sparql.function.FunctionRegistry;
import com.hp.hpl.jena.sparql.pfunction.PropertyFunctionRegistry;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.update.UpdateAction;

import eu.iksproject.kres.api.manager.DuplicateIDException;
import eu.iksproject.kres.api.manager.KReSONManager;
import eu.iksproject.kres.api.manager.ontology.OntologyScope;
import eu.iksproject.kres.api.manager.ontology.OntologyScopeFactory;
import eu.iksproject.kres.api.manager.ontology.OntologySpaceFactory;
import eu.iksproject.kres.api.manager.ontology.ScopeRegistry;
import eu.iksproject.kres.api.manager.session.DuplicateSessionIDException;
import eu.iksproject.kres.api.manager.session.KReSSession;
import eu.iksproject.kres.api.manager.session.KReSSessionManager;
import eu.iksproject.kres.api.reasoners.InconcistencyException;
import eu.iksproject.kres.api.reasoners.KReSReasoner;
import eu.iksproject.kres.api.rules.KReSRule;
import eu.iksproject.kres.api.rules.NoSuchRecipeException;
import eu.iksproject.kres.api.rules.Recipe;
import eu.iksproject.kres.api.rules.RuleStore;
import eu.iksproject.kres.api.rules.util.KReSRuleList;
import eu.iksproject.kres.api.semion.SemionManager;
import eu.iksproject.kres.api.semion.SemionRefactorer;
import eu.iksproject.kres.api.semion.SemionRefactoringException;
import eu.iksproject.kres.api.semion.util.URIGenerator;
import eu.iksproject.kres.api.storage.OntologyStorage;
import eu.iksproject.kres.rules.arqextention.CreatePropertyURIStringFromLabel;
import eu.iksproject.kres.rules.arqextention.CreateStandardLabel;
import eu.iksproject.kres.rules.arqextention.CreateURI;
import eu.iksproject.kres.shared.transformation.JenaToClerezzaConverter;
import eu.iksproject.kres.shared.transformation.JenaToOwlConvert;
import eu.iksproject.kres.shared.transformation.OWLAPIToClerezzaConverter;

/** 
 * The SemionRefactorerImpl is the concrete implementation of the
 * SemionRefactorer interface defined in the KReS APIs. A SemionRefacter is able
 * to perform ontology refactorings and mappings.
 * 
 * @author andrea.nuzzolese
 *
 */

@Component(immediate = true, metatype = true)
@Service(SemionRefactorer.class)
public class SemionRefactorerImpl implements SemionRefactorer {

	public static final String _AUTO_GENERATED_ONTOLOGY_IRI_DEFAULT = "http://kres.iksproject.eu/semion/autoGeneratedOntology";
	public static final String _HOST_NAME_AND_PORT_DEFAULT = "localhost:8080";
	public static final String _REFACTORING_SCOPE_DEFAULT = "refactoring";
	public static final String _REFACTORING_SESSION_ID_DEFAULT = "http://kres.iksproject.eu/session/refactoring";
	public static final String _REFACTORING_SPACE_DEFAULT = "http://kres.iksproject.eu/space/refactoring";
	
	@Property(value = _AUTO_GENERATED_ONTOLOGY_IRI_DEFAULT)
	public static final String AUTO_GENERATED_ONTOLOGY_IRI = "eu.iksproject.kres.semion.default";
	
	@Property(value = _HOST_NAME_AND_PORT_DEFAULT)
	public static final String HOST_NAME_AND_PORT = "host.name.port";

	@Property(_REFACTORING_SCOPE_DEFAULT)
    public static final String REFACTORING_SCOPE = "eu.iksproject.kres.scope.refactoring";
	
	@Property(value = _REFACTORING_SESSION_ID_DEFAULT)
	public static final String REFACTORING_SESSION_ID = "eu.iksproject.kres.session.refactoring";

	@Property(value = _REFACTORING_SPACE_DEFAULT)
    public static final String REFACTORING_SPACE = "eu.iksproject.kres.space.refactoring";
	
	private IRI defaultRefactoringIRI;

	private IRI kReSSessionID;
	
	private final Logger log = LoggerFactory.getLogger(getClass());
	
	private IRI refactoringScopeIRI;
	
	private IRI refactoringSpaceIRI;
	
	private OntologyScope scope;
	
	@Reference
	KReSReasoner kReSReasoner;
	@Reference
	KReSONManager onManager;
	@Reference
	RuleStore ruleStore;
	@Reference
	SemionManager semionManager;
	@Reference
	protected Serializer serializer;
	@Reference
	protected TcManager tcManager;
	@Reference
	protected WeightedTcProvider weightedTcProvider;
	
	/**
	 * This default constructor is <b>only</b> intended to be used by the OSGI
	 * environment with Service Component Runtime support.
	 * <p>
	 * DO NOT USE to manually create instances - the SemionRefactorerImpl
	 * instances do need to be configured! YOU NEED TO USE
	 * {@link #SemionRefactorerImpl(WeightedTcProvider, Serializer, TcManager, KReSONManager, SemionManager, RuleStore, KReSReasoner, Dictionary)}
	 * or its overloads, to parse the configuration and then initialise the rule
	 * store if running outside a OSGI environment.
	 */
	public SemionRefactorerImpl() {
		
	}
	
	/**
	 * Basic constructor to be used if outside of an OSGi environment. Invokes
	 * default constructor.
	 * 
	 * @param weightedTcProvider
	 * @param serializer
	 * @param tcManager
	 * @param onManager
	 * @param semionManager
	 * @param ruleStore
	 * @param kReSReasoner
	 * @param configuration
	 */
	public SemionRefactorerImpl(WeightedTcProvider weightedTcProvider,
			Serializer serializer, TcManager tcManager,
			KReSONManager onManager, SemionManager semionManager,
			RuleStore ruleStore, KReSReasoner kReSReasoner,
			Dictionary<String, Object> configuration) {
		this();
		this.weightedTcProvider = weightedTcProvider;
		this.serializer = serializer;
		this.tcManager = tcManager;
		this.onManager = onManager;
		this.semionManager = semionManager;
		this.ruleStore = ruleStore;
		this.kReSReasoner = kReSReasoner;
		activate(configuration);
	}
	
	/**
	 * Used to configure an instance within an OSGi container.
	 * 
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	@Activate
	protected void activate(ComponentContext context) throws IOException {
		log.info("in " + SemionRefactorerImpl.class + " activate with context "
				+ context);
		if (context == null) {
			throw new IllegalStateException("No valid" + ComponentContext.class
					+ " parsed in activate!");
		}
		activate((Dictionary<String, Object>) context.getProperties());
	}
		
	/*
	 * public void consistentOntologyRefactoring(IRI refactoredOntologyIRI, IRI
	 * datasetURI, IRI recipeIRI) throws SemionRefactoringException,
	 * NoSuchRecipeException, InconcistencyException {
	 * 
	 * 
	 * 
	 * OWLOntology refactoredOntology = null;
	 * 
	 * OntologyStorage ontologyStorage = onManager.getOntologyStore();
	 * 
	 * OWLOntology owlOntology = ontologyStorage.load(datasetURI);
	 * 
	 * JenaToOwlConvert jenaToOwlConvert = new JenaToOwlConvert();
	 * 
	 * OntModel ontModel = jenaToOwlConvert.ModelOwlToJenaConvert(owlOntology,
	 * "RDF/XML");
	 * 
	 * Recipe recipe; try { recipe = ruleStore.getRecipe(recipeIRI);
	 * 
	 * KReSRuleList kReSRuleList = recipe.getkReSRuleList();
	 * 
	 * OWLOntologyManager ontologyManager =
	 * OWLManager.createOWLOntologyManager();
	 * 
	 * for(KReSRule kReSRule : kReSRuleList){ String sparql =
	 * kReSRule.toSPARQL();
	 * 
	 * Query sparqlQuery = QueryFactory.create(sparql); QueryExecution qexec =
	 * QueryExecutionFactory.create(sparqlQuery, ontModel) ; Model
	 * refactoredModel = qexec.execConstruct();
	 * 
	 * 
	 * OWLOntology refactoredDataSet =
	 * jenaToOwlConvert.ModelJenaToOwlConvert(refactoredModel, "RDF/XML");
	 * 
	 * ByteArrayOutputStream out = new ByteArrayOutputStream(); try {
	 * ontologyManager.saveOntology(refactoredDataSet, new
	 * RDFXMLOntologyFormat(), out); } catch (OWLOntologyStorageException e) {
	 * // TODO Auto-generated catch block e.printStackTrace(); }
	 * 
	 * ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
	 * 
	 * try { ontologyManager.loadOntologyFromOntologyDocument(in); } catch
	 * (OWLOntologyCreationException e) { // TODO Auto-generated catch block
	 * e.printStackTrace(); }
	 * 
	 * }
	 * 
	 * OWLOntologyMerger merger = new OWLOntologyMerger(ontologyManager);
	 * 
	 * try { refactoredOntology = merger.createMergedOntology(ontologyManager,
	 * refactoredOntologyIRI);
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * if(!kReSReasoner.consistencyCheck(kReSReasoner.getReasoner(refactoredOntology
	 * ))){ throw newInconcistencyException(
	 * "Semion Refactorer : the refactored data set seems to be inconsistent");
	 * } else{ ontologyStorage.store(refactoredOntology); } } catch
	 * (OWLOntologyCreationException e) { // TODO Auto-generated catch block
	 * e.printStackTrace(); }
	 * 
	 * 
	 * 
	 * } catch (NoSuchRecipeException e1) {
	 * log.error("SemionRefactorer : No Such recipe in the KReS Rule Store",
	 * e1); throw e1; }
	 * 
	 * if(refactoredOntology == null){ throw new SemionRefactoringException(); }
	 * 
	 * }
	 */
		
	protected void activate(Dictionary<String, Object> configuration) {
		String refactoringSessionID = (String) configuration
				.get(REFACTORING_SESSION_ID);
		if (refactoringSessionID == null)
			refactoringSessionID = _REFACTORING_SESSION_ID_DEFAULT;
		String refactoringScopeID = (String) configuration
				.get(REFACTORING_SCOPE);
		if (refactoringScopeID == null)
			refactoringScopeID = _REFACTORING_SCOPE_DEFAULT;
		String refactoringSpaceID = (String) configuration
				.get(REFACTORING_SPACE);
		if (refactoringSpaceID == null)
			refactoringSpaceID = _REFACTORING_SPACE_DEFAULT;
		String defaultRefactoringID = (String) configuration
				.get(AUTO_GENERATED_ONTOLOGY_IRI);
		if (defaultRefactoringID == null)
			defaultRefactoringID = _AUTO_GENERATED_ONTOLOGY_IRI_DEFAULT;
		String hostPort = (String) configuration.get(HOST_NAME_AND_PORT);
		if (hostPort == null)
			hostPort = _HOST_NAME_AND_PORT_DEFAULT;
			
		kReSSessionID = IRI.create(refactoringSessionID);
		refactoringScopeIRI = IRI.create("http://" + hostPort
				+ "/kres/ontology/" + refactoringScopeID);
		refactoringSpaceIRI = IRI.create(refactoringSpaceID);
		defaultRefactoringIRI = IRI.create(defaultRefactoringID);
			
		KReSSessionManager kReSSessionManager = onManager.getSessionManager();
			
		KReSSession kReSSession = kReSSessionManager.getSession(kReSSessionID);

		if (kReSSession == null) {
				try {
				kReSSession = kReSSessionManager.createSession(kReSSessionID);
			} catch (DuplicateSessionIDException e) {
				log
						.error(
								"SemionRefactorer : a KReS session for reengineering seems already existing",
								e);
					}
				}
				
		kReSSessionID = kReSSession.getID();
				
		OntologyScopeFactory ontologyScopeFactory = onManager
				.getOntologyScopeFactory();
				
		ScopeRegistry scopeRegistry = onManager.getScopeRegistry();
			
		OntologySpaceFactory ontologySpaceFactory = onManager
				.getOntologySpaceFactory();
			
		scope = null;
			try {
			log.info("Semion DBExtractor : created scope with IRI "
					+ REFACTORING_SCOPE);
				
			scope = ontologyScopeFactory.createOntologyScope(
					refactoringScopeIRI, null);
			
			scopeRegistry.registerScope(scope);
		} catch (DuplicateIDException e) {
			log.info("Semion DBExtractor : already existing scope for IRI "
					+ REFACTORING_SCOPE);
			scope = onManager.getScopeRegistry().getScope(refactoringScopeIRI);
			}
			
		scope.addSessionSpace(ontologySpaceFactory
				.createSessionOntologySpace(refactoringSpaceIRI), kReSSession
				.getID());
				
		scopeRegistry.setScopeActive(refactoringScopeIRI, true);
			
		semionManager.registerRefactorer(this);
		
		
		PropertyFunctionRegistry.get().put("http://www.stlab.istc.cnr.it/semion/function#createURI", CreateURI.class);
		FunctionRegistry.get().put("http://www.stlab.istc.cnr.it/semion/function#createLabel", CreateStandardLabel.class);
		FunctionRegistry.get().put("http://www.stlab.istc.cnr.it/semion/function#propString", CreatePropertyURIStringFromLabel.class);
		
		
		log.info("Activated KReS Semion Refactorer");
	}
	
	@Override
	public void consistentOntologyRefactoring(IRI refactoredOntologyIRI,
			IRI datasetURI, IRI recipeIRI) throws SemionRefactoringException,
			NoSuchRecipeException, InconcistencyException {
		
		OWLOntology refactoredOntology = null;
		
		OntologyStorage ontologyStorage = onManager.getOntologyStore();
		
		Recipe recipe;
		try {
			recipe = ruleStore.getRecipe(recipeIRI);
			
			KReSRuleList kReSRuleList = recipe.getkReSRuleList();
			
			OWLOntologyManager ontologyManager = OWLManager
					.createOWLOntologyManager();
			
			String fingerPrint = "";
			for(KReSRule kReSRule : kReSRuleList){
				String sparql = kReSRule.toSPARQL();
				
				OWLOntology refactoredDataSet = ontologyStorage
						.sparqlConstruct(sparql, datasetURI.toString());
				
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				try {
					ontologyManager.saveOntology(refactoredDataSet,
							new RDFXMLOntologyFormat(), out);
					if (refactoredOntologyIRI == null) {
						ByteArrayOutputStream fpOut = new ByteArrayOutputStream();
						fingerPrint += URIGenerator.createID("", fpOut
								.toByteArray());
					}

				} catch (OWLOntologyStorageException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				ByteArrayInputStream in = new ByteArrayInputStream(out
						.toByteArray());
				
				try {
					ontologyManager.loadOntologyFromOntologyDocument(in);
				} catch (OWLOntologyCreationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
			
			if (refactoredOntologyIRI == null) {
				refactoredOntologyIRI = IRI.create(URIGenerator.createID(
						"urn://", fingerPrint.getBytes()));
			}
			OWLOntologyMerger merger = new OWLOntologyMerger(ontologyManager);
			
			try {
			
				refactoredOntology = merger.createMergedOntology(
						ontologyManager, refactoredOntologyIRI);
			
				if (!kReSReasoner.consistencyCheck(kReSReasoner
						.getReasoner(refactoredOntology))) {
					throw new InconcistencyException(
							"Semion Refactorer : the refactored data set seems to be inconsistent");
				} else {
					ontologyStorage.store(refactoredOntology);
				}
			} catch (OWLOntologyCreationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		} catch (NoSuchRecipeException e1) {
			log.error(
					"SemionRefactorer : No Such recipe in the KReS Rule Store",
					e1);
			throw e1;
		}
		
		if(refactoredOntology == null){
			throw new SemionRefactoringException();
		}
		
		/*
		 * UriRef uriRef = new UriRef(refactoredDataSetURI);
		 * 
		 * MGraph mGraph = weightedTcProvider.createMGraph(datasetURI);
		 * 
		 * Set<IRI> ruleIRIs = kReSRuleManager.getRecipe(recipeIRI);
		 * 
		 * for(IRI ruleIRI : ruleIRIs){ KReSRule kReSRule =
		 * kReSRuleManager.getRule(ruleIRI);
		 * 
		 * String sparql = kReSRule.toSPARQL();
		 * 
		 * Query query; try { query = QueryParser.getInstance().parse(sparql);
		 * MGraph dataset = weightedTcProvider.getMGraph(datasetURI);
		 * mGraph.addAll((SimpleGraph) tcManager.executeSparqlQuery(query,
		 * dataset));
		 * 
		 * } catch (ParseException e) { // TODO Auto-generated catch block
		 * e.printStackTrace(); } }
		 * 
		 * ByteArrayOutputStream out = new ByteArrayOutputStream();
		 * 
		 * SerializingProvider serializingProvider = new
		 * JenaSerializerProvider();
		 * 
		 * serializingProvider.serialize(out, mGraph, SupportedFormat.RDF_XML);
		 * 
		 * ByteArrayInputStream in = new
		 * ByteArrayInputStream(out.toByteArray());
		 * 
		 * OWLOntologyManager owlOntologyManager =
		 * onManager.getOwlCacheManager();
		 * 
		 * OWLOntology owlmodel; try { owlmodel =
		 * owlOntologyManager.loadOntologyFromOntologyDocument(in);
		 * if(kReSReasoner
		 * .consistencyCheck(kReSReasoner.getReasoner(owlmodel))){ return
		 * uriRef; } else{ throw newInconcistencyException(
		 * "Semion Refactorer : the refactored data set seems to be inconsistent"
		 * ); } } catch (OWLOntologyCreationException e) { throw new
		 * InconcistencyException
		 * ("Semion Refactorer : the refactored data set seems to be invalid");
		 * }
		 */
	
	}
	
	@Override
	public OWLOntology consistentOntologyRefactoring(OWLOntology inputOntology,
			IRI recipeIRI) throws SemionRefactoringException,
			NoSuchRecipeException, InconcistencyException {
		
		OWLOntology refactoredOntology = null;
		
		JenaToOwlConvert jenaToOwlConvert = new JenaToOwlConvert();

		OntModel ontModel = jenaToOwlConvert.ModelOwlToJenaConvert(
				inputOntology, "RDF/XML");
		
		Recipe recipe;
		try {
			recipe = ruleStore.getRecipe(recipeIRI);
			
			KReSRuleList kReSRuleList = recipe.getkReSRuleList();
			
			OWLOntologyManager ontologyManager = OWLManager
					.createOWLOntologyManager();
			
			for(KReSRule kReSRule : kReSRuleList){
				String sparql = kReSRule.toSPARQL();
				
				Query sparqlQuery = QueryFactory.create(sparql);
				QueryExecution qexec = QueryExecutionFactory.create(
						sparqlQuery, ontModel);
				Model refactoredModel = qexec.execConstruct();
				
				OWLOntology refactoredDataSet = jenaToOwlConvert
						.ModelJenaToOwlConvert(refactoredModel, "RDF/XML");

				ByteArrayOutputStream out = new ByteArrayOutputStream();
				try {
					ontologyManager.saveOntology(refactoredDataSet,
							new RDFXMLOntologyFormat(), out);
				} catch (OWLOntologyStorageException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				ByteArrayInputStream in = new ByteArrayInputStream(out
						.toByteArray());
				
				try {
					ontologyManager.loadOntologyFromOntologyDocument(in);
				} catch (OWLOntologyCreationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
			
			OWLOntologyMerger merger = new OWLOntologyMerger(ontologyManager);
			
			try {
				IRI defaultOntologyIRI = IRI
						.create("http://kres.iksproject.eu/semion/autoGeneratedOntology");
				refactoredOntology = merger.createMergedOntology(
						ontologyManager, defaultOntologyIRI);

				if (!kReSReasoner.consistencyCheck(kReSReasoner
						.getReasoner(refactoredOntology))) {
					throw new InconcistencyException(
							"Semion Refactorer : the refactored data set seems to be inconsistent");
				}

			} catch (OWLOntologyCreationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		} catch (NoSuchRecipeException e1) {
			log.error(
					"SemionRefactorer : No Such recipe in the KReS Rule Store",
					e1);
			throw e1;
		}
		
		if(refactoredOntology == null){
			throw new SemionRefactoringException();
		} else {
			return refactoredOntology;
		}
	}
	
	@Deactivate
	protected void deactivate(ComponentContext context){
		log.info("in " + SemionRefactorerImpl.class
				+ " deactivate with context " + context);
		
		KReSSessionManager kReSSessionManager = onManager.getSessionManager();
		kReSSessionManager.destroySession(kReSSessionID);
		semionManager.unregisterRefactorer();
		
		this.weightedTcProvider = null;
		this.serializer = null;
		this.tcManager = null;
		this.onManager = null;
		this.ruleStore = null;
		this.kReSReasoner = null;
	}

	@Override
	public MGraph getRefactoredDataSet(UriRef uriRef) {

		return weightedTcProvider.getMGraph(uriRef);
	}

	@Override
	public void ontologyRefactoring(IRI refactoredOntologyIRI, IRI datasetURI,
			IRI recipeIRI) throws SemionRefactoringException,
			NoSuchRecipeException {
		
		OWLOntology refactoredOntology = null;
		
		OntologyStorage ontologyStorage = onManager.getOntologyStore();
		
		Recipe recipe;
		try {
			recipe = ruleStore.getRecipe(recipeIRI);
			
			KReSRuleList kReSRuleList = recipe.getkReSRuleList();
			
			OWLOntologyManager ontologyManager = OWLManager
					.createOWLOntologyManager();
			
			String fingerPrint = "";
			for(KReSRule kReSRule : kReSRuleList){
				String sparql = kReSRule.toSPARQL();
				OWLOntology refactoredDataSet = ontologyStorage
						.sparqlConstruct(sparql, datasetURI.toString());
				
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				try {
					ontologyManager.saveOntology(refactoredDataSet,
							new RDFXMLOntologyFormat(), out);
					if (refactoredOntologyIRI == null) {
						ByteArrayOutputStream fpOut = new ByteArrayOutputStream();
						fingerPrint += URIGenerator.createID("", fpOut
								.toByteArray());
					}

				} catch (OWLOntologyStorageException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				ByteArrayInputStream in = new ByteArrayInputStream(out
						.toByteArray());
				
				try {
					ontologyManager.loadOntologyFromOntologyDocument(in);
				} catch (OWLOntologyCreationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
			
			if (refactoredOntologyIRI == null) {
				refactoredOntologyIRI = IRI.create(URIGenerator.createID(
						"urn://", fingerPrint.getBytes()));
			}
			OWLOntologyMerger merger = new OWLOntologyMerger(ontologyManager);
			
			try {
			
				refactoredOntology = merger.createMergedOntology(
						ontologyManager, refactoredOntologyIRI);
			
				ontologyStorage.store(refactoredOntology);
				
			} catch (OWLOntologyCreationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		} catch (NoSuchRecipeException e1) {
			log.error(
					"SemionRefactorer : No Such recipe in the KReS Rule Store",
					e1);
			throw e1;
		}
		
		if(refactoredOntology == null){
			throw new SemionRefactoringException();
		}
		}

	@Override
	public OWLOntology ontologyRefactoring(OWLOntology inputOntology,
			IRI recipeIRI) throws SemionRefactoringException,
			NoSuchRecipeException {
		OWLOntology refactoredOntology = null;
		
		//JenaToOwlConvert jenaToOwlConvert = new JenaToOwlConvert();
		
		// OntModel ontModel =
		// jenaToOwlConvert.ModelOwlToJenaConvert(inputOntology, "RDF/XML");
		
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		
		Recipe recipe;
		try {
			recipe = ruleStore.getRecipe(recipeIRI);
			
			KReSRuleList kReSRuleList = recipe.getkReSRuleList();
			log.info("RULE LIST SIZE : "+kReSRuleList.size());
			
			OWLOntologyManager ontologyManager = OWLManager
					.createOWLOntologyManager();
			OWLOntologyManager ontologyManager2 = OWLManager
					.createOWLOntologyManager();
			
			MGraph unionMGraph = new SimpleMGraph();
			
			MGraph mGraph = OWLAPIToClerezzaConverter
					.owlOntologyToClerezzaMGraph(inputOntology);
			
			for(KReSRule kReSRule : kReSRuleList){
				String sparql = kReSRule.toSPARQL();
				log.info("SPARQL : "+sparql);
				
				Graph constructedGraph = null;
				
				switch (kReSRule.getExpressiveness()) {
				case KReSCore:
					constructedGraph = kReSCoreOperation(sparql, mGraph);
					break;
				case ForwardChaining:
					constructedGraph = forwardChainingOperation(sparql, mGraph);
					break;
				case Reflexive:
					constructedGraph = kReSCoreOperation(sparql, unionMGraph);
					break;
				case SPARQLConstruct:
					constructedGraph = kReSCoreOperation(sparql, mGraph);
					break;
				case SPARQLDelete:
					constructedGraph = sparqlUpdateOperation(sparql, unionMGraph);
					break;
				case SPARQLDeleteData:
					constructedGraph = sparqlUpdateOperation(sparql, unionMGraph);
					break;
				default:
					break;
				}
				
				
				if(constructedGraph != null){
					unionMGraph.addAll(constructedGraph);
				}
				
			}
				
			refactoredOntology = OWLAPIToClerezzaConverter
					.clerezzaMGraphToOWLOntology(unionMGraph);
			
		} catch (NoSuchRecipeException e1) {
			e1.printStackTrace();
			log.error(
					"SemionRefactorer : No Such recipe in the KReS Rule Store",
					e1);
			throw e1;
		}
		
		if(refactoredOntology == null){
			throw new SemionRefactoringException();
		} else {
			return refactoredOntology;
		}
	}
	
	
	
	private Graph kReSCoreOperation(String query, MGraph mGraph){
		
		/*
		
		Graph constructedGraph = null;
		try {
			ConstructQuery constructQuery = (ConstructQuery) QueryParser.getInstance()
			.parse(query);
			constructedGraph = tcManager.executeSparqlQuery(
					constructQuery, mGraph);
			
		} catch (ParseException e) {
			log.error(e.getMessage());
		} catch (NoQueryEngineException e) {
			log.error(e.getMessage());
		}
		
		return constructedGraph;
		*/
		
		
		Model model = JenaToClerezzaConverter.clerezzaMGraphToJenaModel(mGraph);
		
		Query sparqlQuery = QueryFactory.create(query, Syntax.syntaxARQ);
		QueryExecution qexec = QueryExecutionFactory.create(sparqlQuery, model) ;
		
		return JenaToClerezzaConverter.jenaModelToClerezzaMGraph(qexec.execConstruct()).getGraph();
		
		
	}
	
	
	private Graph forwardChainingOperation(String query, MGraph mGraph){
		
		Graph graph = kReSCoreOperation(query, mGraph);
		
		mGraph.addAll(graph);
		
		return graph;
	}
	
	private Graph sparqlUpdateOperation(String query, MGraph mGraph){
		Model model = JenaToClerezzaConverter.clerezzaMGraphToJenaModel(mGraph);
		UpdateAction.parseExecute(query, model);
		return JenaToClerezzaConverter.jenaModelToClerezzaMGraph(model).getGraph();
	}

}
