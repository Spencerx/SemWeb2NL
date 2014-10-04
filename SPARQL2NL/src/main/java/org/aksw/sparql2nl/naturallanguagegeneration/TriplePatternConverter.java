/**
 * 
 */
package org.aksw.sparql2nl.naturallanguagegeneration;

import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.aksw.triple2nl.TripleConverter;
import org.aksw.triple2nl.DefaultIRIConverter;
import org.aksw.triple2nl.property.PredicateAsNounConversionType;
import org.aksw.triple2nl.property.PropertyVerbalization;
import org.aksw.triple2nl.property.PropertyVerbalizationType;
import org.aksw.triple2nl.property.PropertyVerbalizer;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simplenlg.features.Feature;
import simplenlg.features.InternalFeature;
import simplenlg.features.Tense;
import simplenlg.framework.LexicalCategory;
import simplenlg.framework.NLGElement;
import simplenlg.framework.NLGFactory;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.PPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.phrasespec.VPPhraseSpec;
import simplenlg.realiser.english.Realiser;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.vocabulary.RDFS;

/**
 * Convert triple(s) into natural language.
 * @author Lorenz Buehmann
 * 
 */
public class TriplePatternConverter {
	
	private static final Logger logger = LoggerFactory.getLogger(TriplePatternConverter.class);

	private NLGFactory nlgFactory;
	private Realiser realiser;

	private TripleConverter tripleConverter;
	private PropertyVerbalizer pp;

	private boolean useCompactOfVerbalization = true;

	public TriplePatternConverter(SparqlEndpoint endpoint, String cacheDirectory, String wordnetDirectory, Lexicon lexicon) {
		this(new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs()), null,
				null, cacheDirectory, wordnetDirectory, lexicon);
	}
	
	public TriplePatternConverter(SparqlEndpoint endpoint, String cacheDirectory, String wordnetDirectory) {
		this(new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs()), 
				null, null, cacheDirectory, wordnetDirectory, Lexicon.getDefaultLexicon());
	}

	public TriplePatternConverter(QueryExecutionFactory qef, DefaultIRIConverter uriConverter, String cacheDirectory, String wordnetDirectory) {
		this(qef, null, uriConverter, cacheDirectory, wordnetDirectory, Lexicon.getDefaultLexicon());
	}
	
	public TriplePatternConverter(QueryExecutionFactory qef, String cacheDirectory, Lexicon lexicon) {
		this(qef, null, null, cacheDirectory, null, lexicon);
	}
	
	public TriplePatternConverter(QueryExecutionFactory qef, PropertyVerbalizer propertyVerbalizer, DefaultIRIConverter uriConverter, String cacheDirectory, String wordnetDirectory, Lexicon lexicon) {
		tripleConverter = new TripleConverter(qef, propertyVerbalizer, uriConverter, cacheDirectory, wordnetDirectory, lexicon);
		
		if(propertyVerbalizer == null){
			propertyVerbalizer = new PropertyVerbalizer(uriConverter, wordnetDirectory);
		}
		pp = propertyVerbalizer;
		
		nlgFactory = new NLGFactory(lexicon);
		realiser = new Realiser(lexicon);
	}
	
	public NPPhraseSpec convertTriplePattern(Triple t, NLGElement subjectElement, NLGElement objectElement, boolean plural, boolean negated, boolean reverse) {
		return convertTriplePattern(t, subjectElement, objectElement, plural, negated, reverse, PredicateAsNounConversionType.RELATIVE_CLAUSE_COMPLEMENTIZER);
	}
	
	/**
	 * Convert a triple pattern into "v(PREDICATE)s of v(OBJECT)"
	 * @param t the triple
	 * @param negated if phrase is negated 
	 * @return the phrase
	 */
	public NPPhraseSpec convertTriplePatternCompactOfForm(Triple t, NLGElement objectElement) {
		// handle the predicate
		PropertyVerbalization propertyVerbalization = pp.verbalize(t.getPredicate().getURI());
		String predicateAsString = propertyVerbalization.getVerbalizationText();

		NPPhraseSpec np = nlgFactory.createNounPhrase(predicateAsString);
		PPPhraseSpec pp = nlgFactory.createPrepositionPhrase("of", objectElement);
		np.addComplement(pp);
		return np;
	}
	
	
	/**
	 * Convert a triple pattern into a phrase object
	 * @param t the triple pattern
	 * @param negated if phrase is negated 
	 * @return the phrase
	 */
	public NPPhraseSpec convertTriplePattern(Triple t, NLGElement subjectElement, NLGElement objectElement, boolean plural, boolean negated, boolean reverse, PredicateAsNounConversionType nounPredicateConversion) {
		NPPhraseSpec np = nlgFactory.createNounPhrase(realiser.realise(subjectElement).getRealisation());
		SPhraseSpec clause = null;
		
		Node subject = t.getSubject();
		Node predicate = t.getPredicate();
		Node object = t.getObject();
		
		//if there is no object element we convert it into the existence of the triple pattern
		if(objectElement == null){
//			objectElement = processObject(object, false);
		}

		// process predicate
		// start with variables
		if (predicate.isVariable()) {
			clause = nlgFactory.createClause(null, "be related via " + predicate.toString() + " to", objectElement);
		} // more interesting case. Predicate is not a variable
			// then check for noun and verb. If the predicate is a noun or a
			// verb, then
			// use possessive or verbal form, else simply get the boa pattern
		else {
			// handle the predicate
			PropertyVerbalization propertyVerbalization = pp.verbalize(predicate.getURI());
			String predicateAsString = propertyVerbalization.getVerbalizationText();

			// get the lexicalization type of the predicate
			PropertyVerbalizationType type;
			if (predicate.matches(RDFS.label.asNode())) {
				type = PropertyVerbalizationType.NOUN;
			} else {
				type = propertyVerbalization.getVerbalizationType();
			}

			/*-
			 * if the predicate is a noun we generate a possessive form, i.e. 'SUBJECT'S PREDICATE be OBJECT'
			 */
			clause = nlgFactory.createClause();
			np.addComplement(clause);
			if (type == PropertyVerbalizationType.NOUN) {
				//reversed triple pattern -> SUBJECT that be NP of OBJECT
				if(reverse){
					VPPhraseSpec vp = nlgFactory.createVerbPhrase("be");
					vp.setIndirectObject(nlgFactory.createNounPhrase(predicateAsString));
					clause.setVerbPhrase(vp);
					PPPhraseSpec pp = nlgFactory.createPrepositionPhrase("of", objectElement);
					clause.setObject(pp);
				} else {
					if(objectElement == null){
						VPPhraseSpec vp = nlgFactory.createVerbPhrase("have");
						vp.setIndirectObject(nlgFactory.createNounPhrase("a", predicateAsString));
						clause.setVerbPhrase(vp);
						clause.setObject(objectElement);
					} else {
						//if predicate ends with "of" -> SUBJECT be PREDICATE OBJECT
						if(predicateAsString.endsWith(" of")){
							if(useCompactOfVerbalization){
								predicateAsString = predicateAsString.replaceFirst("( of)$", "");
								np.setNoun(predicateAsString);
								PPPhraseSpec pp = nlgFactory.createPrepositionPhrase("of", objectElement);
								np.clearComplements();
								np.setComplement(pp);
							} else {
								VPPhraseSpec vp = nlgFactory.createVerbPhrase("be");
								// vp.setFeature(Feature.PROGRESSIVE, true);
								vp.setIndirectObject(nlgFactory.createNounPhrase(predicateAsString));
								clause.setVerbPhrase(vp);
								clause.setObject(objectElement);
							}
						} else {
							switch (nounPredicateConversion) {
							case RELATIVE_CLAUSE_COMPLEMENTIZER: {
								VPPhraseSpec vp = nlgFactory.createVerbPhrase("have");
								vp.setIndirectObject(nlgFactory.createNounPhrase(predicateAsString));
								clause.setVerbPhrase(vp);
								clause.setObject(objectElement);
							}
								break;
							case RELATIVE_CLAUSE_PRONOUN: {
								clause.setSubject(predicateAsString);
								clause.setVerb("be");
								clause.setObject(objectElement);
								NLGElement complementiser = nlgFactory.createInflectedWord("whose", LexicalCategory.PRONOUN);
								complementiser.setFeature(InternalFeature.NON_MORPH, true);
								complementiser.setFeature(Feature.POSSESSIVE, true);
								clause.setFeature(Feature.COMPLEMENTISER, complementiser);
							}
								break;
							case REDUCED_RELATIVE_CLAUSE: {
								VPPhraseSpec vp = nlgFactory.createVerbPhrase("having");
								// vp.setFeature(Feature.PROGRESSIVE, true);
								vp.setIndirectObject(nlgFactory.createNounPhrase(predicateAsString));
								clause.setVerbPhrase(vp);
								clause.setObject(objectElement);
							}
								break;
							default:
								;
							}
						}
					}
				}
			}// if the predicate is a verb
			else if (type == PropertyVerbalizationType.VERB) {
				clause.setVerb(predicateAsString);
				clause.setObject(objectElement);
			}
		}
		
		//check if the meaning of the triple is it's negation, which holds for boolean properties with FALSE as value
		if(!negated){
			//check if object is boolean literal
			if(object.isLiteral() && object.getLiteralDatatype() != null && object.getLiteralDatatype().equals(XSDDatatype.XSDboolean)){
				//omit the object
				clause.setObject(null);
				
				negated = !(boolean) object.getLiteralValue();
				
			}
		}
		
		//set negation
		if(negated){
			clause.setFeature(Feature.NEGATED, negated);
		}
		
		//set present time as tense
		clause.setFeature(Feature.TENSE, Tense.PRESENT);
		np.setPlural(plural);
		clause.setPlural(plural);

		return np;
	}
	
	public NLGElement processNode(Node node) {
		return tripleConverter.processNode(node);
	}
	
	public NPPhraseSpec processClassNode(Node node, boolean plural) {
		return tripleConverter.processClassNode(node, plural);
	}
	
	/**
	 * Set if string literal values are encapsulated in quotes.
	 * @param encapsulateStringLiterals the encapsulateStringLiterals to set
	 */
	public void setEncapsulateStringLiterals(boolean encapsulateStringLiterals) {
		this.tripleConverter.setEncapsulateStringLiterals(encapsulateStringLiterals);
	}
	
	/**
	 * Show language as adjective for literals, e.g. "...English label..."
	 * @param considerLiteralLanguage whether to use language tag in verbalization or not
	 */
	public void setConsiderLiteralLanguage(boolean considerLiteralLanguage) {
		this.tripleConverter.setConsiderLiteralLanguage(considerLiteralLanguage);
	}
	
}
