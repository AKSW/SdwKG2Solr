package sdw.aksw.org.solr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

import aksw.org.kg.KgException;
import aksw.org.kg.StatsRecorder;
import aksw.org.sdw.kg.handler.solr.KgSolrException;
import aksw.org.sdw.kg.handler.solr.KgSorlInputDocument;
import aksw.org.sdw.kg.handler.solr.SolrHandler;
import aksw.org.sdw.kg.handler.solr.SolrUriInputDocument;
import sdw.aksw.org.config.KgSolrConfig;
import sdw.aksw.org.config.KgSolrConfig.KgSolrMapping;

/**
 * This class can be used to load entity information from a SPARQL endpoint.
 * Then this information is stored in a SOLR backend.
 * 
 * @author kay
 *
 */
public class KgJenaEntityToSolrImporter {

	/** counts the number of documents */
	AtomicLong docCount = new AtomicLong();

	/** name of graph which should be queries */
	final List<String> graphNames;

	/** specifies mappings between KG and SOLR */
	final List<KgSolrMapping> mappings;

	/** Restrictions */
	final List<String> restrictions;

	/** sparql endpoint url */
	final String sparqlEndpoint;

	/** solr handler instance which can be used to transfer documents to SOLR */
	final SolrHandler solrHandler;

	/** records statistics for this instance */
	final StatsRecorder statsRecorder = new StatsRecorder();

	/**
	 * Constructor
	 * 
	 * @param sparqlEndpoint
	 *            - sparql endpount url
	 * @param solrHandler
	 *            - solr handler
	 * @param graphName
	 *            - name of graph which should be queried
	 * @param restrictions
	 *            - ensures that only the entities are returned, which are of
	 *            concern
	 * @param mappings
	 *            - mappings between KG and SOLR
	 */
	public KgJenaEntityToSolrImporter(final SolrHandler solrHandler) {
		this.mappings = KgSolrConfig.getInstance().getMappings();

		this.graphNames = KgSolrConfig.getInstance().getGraphNames();

		this.restrictions = KgSolrConfig.getInstance().getRestrictions();

		this.sparqlEndpoint = KgSolrConfig.getInstance().getSparqlUrl();

		this.solrHandler = solrHandler;
	}

	/**
	 * This method can be used to add predicate UNION queries from a map of
	 * predicates and their variable name
	 * 
	 * @param entityUri
	 *            - entity uri
	 * @param query
	 *            - string buffer query object
	 * @param variableName
	 *            - name of SPARQL variable
	 * @param predicateList
	 *            - list of predicate names
	 */
	protected void addUnionQueries(final String entityUri, final StringBuilder query, final String variableName,
			final List<String> predicateList, final boolean isOptional, final AtomicInteger count) {
		if (null == entityUri || null == query || null == variableName || null == predicateList) {
			return;
		}

		boolean isNotFirst = false;
		for (String predicateName : predicateList) {

			if (false == isOptional && isNotFirst) {
				query.append(" UNION\n");
			} else if (isOptional) {
				query.append("\n\tOPTIONAL");
			}

			// create sub-query
			if (predicateName.startsWith("<") && predicateName.endsWith(">")) {
				query.append("\t { <");
				query.append(entityUri);
				query.append("> ");
				query.append(predicateName);
				query.append(" ?");
				query.append(variableName);
				query.append(count);
				query.append(" . }");
			} else {
				query.append("\t { <");
				query.append(entityUri);
				query.append("> <");
				query.append(predicateName);
				query.append("> ?");
				query.append(variableName);
				query.append(count);
				query.append(" . }");
			}

			isNotFirst = true;
			count.incrementAndGet();
		}

		query.append("\n");
	}

	protected void addUnionQueriesForJson(final String entityUri, final StringBuilder query, final String variableName,
			final List<String> predicateList, final boolean isOptional, final AtomicInteger count) {
				
		if (null == entityUri || null == query || null == variableName || null == predicateList) {
			return;
		}
	
		boolean isNotFirst = false;
		for (String predicateName : predicateList) {

//			if (false == isOptional && isNotFirst) {
//				query.append(" UNION\n");
//			} else if (isOptional) {
//				query.append("\n\tOPTIONAL");
//			}

			//changed
			//Format? json? <resource> name=<label> loc=<georss/point> 
			String[] arr = predicateName.split(" ");
			
			query.append(" OPTIONAL\n");
			
			query.append("\t { <");
			query.append(entityUri);
			query.append("> ");
			query.append(arr[0]);
			query.append(" ?");
			query.append(variableName);
			query.append(count);

			
			for( int i = 1 ; i < arr.length ; i++ ) {
				query.append("\t OPTIONAL { ?");
				query.append(variableName+count);
				query.append(" ");
				query.append(arr[i].split("=")[1]);
				query.append(" ?");
				query.append(variableName+arr[i].split("=")[0]+count);
				query.append(" . }");
			}
			
			query.append(" }");
			
			// create sub-query

			isNotFirst = true;
			count.incrementAndGet();
		}
		
		//System.out.println(query.toString());
		
		query.append("\n");
	}

	/**
	 * This method can be return the SPARQL query which contains
	 * 
	 * @return
	 */
	protected String getQuery(final int offset, final int limit) {
		StringBuilder query = new StringBuilder();

		query.append("SELECT * WHERE {\n");
		query.append("{  SELECT * WHERE {\n");

		if (null != this.restrictions) {
			for (String restriction : this.restrictions) {
				query.append("\t?uri " + restriction + " .\n");
			}
		}

		query.append("    } ORDER BY ASC(?uri) }\n");
		query.append("} LIMIT ").append(limit).append(" OFFSET ").append(offset);

		return query.toString();
	}

	protected String getEntityQuery(final String entityUri, List<KgSolrMapping> mappings) {
		StringBuilder query = new StringBuilder();

		query.append("SELECT DISTINCT * WHERE {\n");

		for (KgSolrMapping mapping : mappings) {

			//System.out.println(mapping.kgJsonPredicateNames.toString() + 1);
				
			AtomicInteger count = new AtomicInteger();
			String kgVariableName = mapping.kgVariableName;
			this.addUnionQueries(entityUri, query, kgVariableName, mapping.kgRequiredPredicateNames, false, count);
			this.addUnionQueries(entityUri, query, kgVariableName, mapping.kgOptionalPredicateNames, true, count);
			this.addUnionQueriesForJson(entityUri, query, kgVariableName, mapping.kgJsonPredicateNames, true, count);
		}

		query.append("} LIMIT 10000");
		
		//System.out.println(query.toString());
		
		//System.out.println(query.toString());
		
		//System.exit(0);

		return query.toString();
	}

	/**
	 * This method can be used to load the KG data to SOLR
	 * 
	 * @param uri
	 *            - entity URI
	 * @param graphName
	 *            - name of the graph
	 * @param fieldDataMap
	 *            - field data map which stores data for the field
	 * @throws KgSolrException
	 */
	protected void loadSolrData(final String uri, final String graphName, final Map<String, Set<String>> fieldDataMap)
			throws KgSolrException {
		this.loadSolrData(uri, graphName, fieldDataMap, false);
	}

	protected void loadSolrData(final String uri, final String graphName, final Map<String, Set<String>> fieldDataMap,
			final boolean forceWrite) throws KgSolrException {

		if (null != uri && null != fieldDataMap && false == fieldDataMap.isEmpty()) {
			// create solr doc
			SolrUriInputDocument solrDoc = new SolrUriInputDocument(uri);

			// set URI
			solrDoc.addFieldData("id", uri);
			if (null != graphName) {
				solrDoc.addFieldData("source", graphName);
			}

			// load all the data into the SOLR document
			for (String fieldName : fieldDataMap.keySet()) {
				Set<String> fieldData = fieldDataMap.get(fieldName);
				if (null == fieldData || fieldData.isEmpty()) {
					continue;
				}

				solrDoc.addFieldData(fieldName, fieldData);
			}

			if (this.docCount.incrementAndGet() % 5_000 == 0 || forceWrite) {
				this.solrHandler.addSolrDocument(solrDoc, true);
			} else {
				this.solrHandler.addSolrDocument(solrDoc, false);
			}
		}
	}

	protected Collection<String> getVarNames(final QuerySolution querySolution) {
		if (null == querySolution) {
			return Collections.emptyList();
		}

		// get var names
		List<String> varNames = new ArrayList<>();
		Iterator<String> varNamesIt = querySolution.varNames();
		while (varNamesIt.hasNext()) {
			varNames.add(varNamesIt.next());
		}

		return varNames;
	}

	/**
	 * This method can be used to get matching variable names.
	 * 
	 * @param matchingVarNames
	 *            - matching variable names
	 * @param varNames
	 *            - result RDF variables
	 * @param variableName
	 *            - variable name which should be matched
	 */
	protected void getMatchingVarNames(final Collection<String> matchingVarNames, final Collection<String> varNames,
			final String variableName) {
		for (String varName : varNames) {
			if (varName.startsWith(variableName)) {
				matchingVarNames.add(varName);
			}
		}
	}

	public void transferData() throws KgSolrException, InterruptedException {
		int offset = 0;
		int limit = 10000;

		long startTime = System.currentTimeMillis();
		long entityGroupTime = System.currentTimeMillis();
		for (String graphName : this.graphNames) {

			// set for this iteration
			offset = 0;
			String previousUri = null;

			List<Future<String>> futures = new ArrayList<>();
			ExecutorService executorService = Executors.newFixedThreadPool(12);

			while (true) {

				String queryString = this.getQuery(offset, limit);
				offset += limit;

//				System.out.println("Query graph: " + graphName + " and query: " + queryString);

				QueryExecution queryResult = QueryExecutionFactory.sparqlService(this.sparqlEndpoint, queryString,
						graphName);
				ResultSet selectResult = queryResult.execSelect();
				if (false == selectResult.hasNext()) {
					break;
				}

				while (selectResult.hasNext()) {
					QuerySolution querySolution = selectResult.next();
					
					Resource uri = querySolution.getResource("uri");
					final String uriString = uri.getURI();
					if (null == previousUri || false == previousUri.equals(uriString)) {
						previousUri = uriString;
					} else {
						continue;
					}
					
					final ConcurrentHashMap<String, Set<String>> fieldDataMap = new ConcurrentHashMap<>();
					
					class SparqlSolrEntityLoader implements Callable<String> {


						List<String> matchingVarNames = new ArrayList<>();

						final String uriString;
						
						final List<KgSolrMapping> mappings;

						public SparqlSolrEntityLoader(final String uriString, final List<KgSolrMapping> mappings) {
							this.uriString = uriString;
							this.mappings = mappings;
						}

						@Override
						public String call() throws Exception {

							String entityQueryString = getEntityQuery(this.uriString, this.mappings);

							ResultSet entitySelectResult = null;
							QueryExecution entityQueryResult = null;
							int tryCount = 10;
							do {
								try {
									entityQueryResult = QueryExecutionFactory.sparqlService(sparqlEndpoint,
											entityQueryString, graphName);								
									
									entitySelectResult = entityQueryResult.execSelect();
									if (false == entitySelectResult.hasNext()) {
										// no more results from this graph
										return "";
									}
								} catch (Exception e) {
									if (0 >= tryCount) {
										throw e;
									} else {
										if (null != entityQueryResult) {
											entityQueryResult.close();
											entityQueryResult = null;
										}
										
										Thread.sleep(1000);
									}
								}
							} while ((null == entityQueryResult || null == entitySelectResult) && 0 < tryCount--);

							while (entitySelectResult.hasNext()) {
								QuerySolution entityResult = entitySelectResult.next();

								Collection<String> varNames = getVarNames(entityResult);

								for (KgSolrMapping mapping : this.mappings) {
									String variableName = mapping.kgVariableName;

									this.matchingVarNames.clear();
									getMatchingVarNames(this.matchingVarNames, varNames, variableName);
									if (this.matchingVarNames.isEmpty()) {
										continue;
									}
		
									for (String matchingVarName : this.matchingVarNames) {

										// now repeat for all registered solr
										// mapping groups
										for (int i = 0; i < mapping.solrGroupSize; ++i) {
											RDFNode node = entityResult.get(matchingVarName);
											//matchPattern ?
											if (false == mapping.matches(node, i)  && null == mapping.kgJsonPredicateNames ) {
												continue;
											}

											String solrFieldName = mapping.solrFieldNames.get(i);

											mapping.mappingInstance.fillFieldDataMap(entityResult, mapping,
													matchingVarName, solrFieldName, fieldDataMap);
										}
									}
								}
							}
							entityQueryResult.close();

							return "";
						};
					};
					
					//change
					int blockSize = 2;
					for (int i = 0; i < this.mappings.size(); i += blockSize) {
						
						List<KgSolrMapping> subMapping = ((i + blockSize >= this.mappings.size()) ?  this.mappings.subList(i, this.mappings.size()) : this.mappings.subList(i, i + blockSize));
						
						Future<String> future = null;
						Callable<String> callable = new SparqlSolrEntityLoader(uriString, subMapping);
						
							try {
	
								future = executorService.submit(callable);
	
							} catch (Exception e) {
								throw new RuntimeException("Something happened", e);
							}
	
						if (null != future) {
							futures.add(future);
						}
					}

					for (Future<String> future : futures) {
						try {
							future.get(10_000, TimeUnit.SECONDS);
						} catch (TimeoutException | ExecutionException | InterruptedException e) {
							throw new KgSolrException("Future Exception", e);
						}
					}
					
					futures.clear();

					
					if (false == fieldDataMap.isEmpty()) {
						loadSolrData(uriString, graphName, fieldDataMap);

						statsRecorder.incrementStats(getClass(), "entityCount");
						statsRecorder.incrementStats(getClass(), "graphEntityCount: " + graphName);
					}

					
					long count = statsRecorder.incrementStats(getClass(), "count");
					if (count % 1 == 0) {
						
						long now = System.currentTimeMillis();
						
						long diffAll = now - startTime;
						long diffBlock = now - entityGroupTime;
						
						entityGroupTime = now;

						long entityCount = statsRecorder.getStatsEntry(getClass(),
								"entityCount");
						long graphEntityCount = statsRecorder.getStatsEntry(getClass(),
								"graphEntityCount: " + graphName);
						
						System.out.println("Imported Entities in total: " + entityCount
								+ " Imported Entities per graph: " + graphEntityCount
								+ " average time: " + ((float) entityCount / (diffAll / 1000)) + "[entities/s] "
								+ " block time: " + diffBlock + "[ms]");
						
						// changed
						// solrHandler.commit();
					}
				}
				
				queryResult.close();

			}
		}

		// just insure that all the remaining documents are submitted
		this.solrHandler.commit();

		System.out.println("Got statistics: " + this.statsRecorder.getStatistics());
	}
}
