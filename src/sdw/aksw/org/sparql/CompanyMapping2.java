package sdw.aksw.org.sparql;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.jetty.util.ConcurrentHashSet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

import sdw.aksw.org.config.KgSolrConfig.KgSolrMapping;

public class CompanyMapping2 implements Solr2SparqlMappingInterface {

	static final Pattern bracketsPattern = Pattern.compile("\\(.*?\\)");
	
	@Override
	public void fillFieldDataMap(QuerySolution querySolution, KgSolrMapping mapping, String matchingVarName,
			String solrFieldName, Map<String, Set<String>> fieldDataMap) {

		// System.out.println(querySolution.toString());

		//if (solrFieldName.equals("ceo") || solrFieldName.equals("location") || solrFieldName.equals("headquarter")) {

			String kgVar = mapping.kgVariableName;
			JsonObject jo = new JsonObject();
			
			int index;

			if (matchingVarName.contains(kgVar + "Name")) {
				index = Integer.parseInt(matchingVarName.substring((kgVar + "Name").length()));
			} else if (matchingVarName.contains(kgVar + "Loc")) {

				index = Integer.parseInt(matchingVarName.substring((kgVar + "Loc").length()));

			} else if (matchingVarName.contains(kgVar + "SameAs")) {

				index = Integer.parseInt(matchingVarName.substring((kgVar + "SameAs").length()));
	
			} else {
				index = Integer.parseInt(matchingVarName.substring(kgVar.length()));

			}

			//TODO if uri literal
			
			//uri
			RDFNode uri = querySolution.get(kgVar + index);
			if (null == uri) {
				return;
			}
			
			RDFNode name = querySolution.get(kgVar + "Name" + index);
			String name_str = "";


			RDFNode sameAs = querySolution.get(kgVar + "SameAs" + index);
			String sameAs_str = "";
			
			String uri_str = "";

			if (uri.isLiteral()) {
				uri_str += uri.asLiteral().getLexicalForm().toString();
				if( null == name ) {
					jo.addProperty("name", uri_str);
				}
			} else if (uri.isResource()) {
				uri_str += uri.asResource().toString();
			}
			
			jo.addProperty("uri", uri_str);
			
			if (null != sameAs ) {
				if (sameAs.isLiteral()) {
					sameAs_str += sameAs.asLiteral().getLexicalForm().toString();
					
				} else if (sameAs.isResource()) {
					sameAs_str += sameAs.asResource().toString();
				}
				
				jo.addProperty("sameAs", sameAs_str);
			}
			
			//name
			if ( mapping.matches(name, 0) ) {
			
			if (null != name) {
				if (name.isLiteral()) {
					name_str += bracketsPattern.matcher(name.asLiteral().getLexicalForm()).replaceAll("");;
					
				} else if (name.isResource()) {
					name_str += name.asResource().toString();
				}
				jo.addProperty("name", name_str);
				
				Set<String> fieldData = null;
				lock.lock();
				try {
					fieldData = fieldDataMap.get(kgVar+"Name");

					if (null == fieldData) {
						fieldData = new ConcurrentHashSet<>();
						fieldDataMap.put(kgVar+"Name", fieldData);
					}
				} finally {
					lock.unlock();
				}
				
				fieldData.add(name_str);
			}
			
			Set<String> fieldData = null;
			lock.lock();
			try {
				fieldData = fieldDataMap.get(solrFieldName);
	
				if (null == fieldData) {
					fieldData = new ConcurrentHashSet<>();
					fieldDataMap.put(solrFieldName, fieldData);
				}
			} finally {
				lock.unlock();
			}

			StringBuffer buffer = new StringBuffer();
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
	        gson.toJson(jo, buffer);
			
			fieldData.add(buffer.toString());
			}
		//}

		// Set<String> fieldData = fieldDataMap.get(solrFieldName);
		// if (null == fieldData) {
		// fieldData = new HashSet<>();
		// fieldDataMap.put(solrFieldName, fieldData);
		// }
		//
		// if (node.isLiteral()) {
		// Literal literal = node.asLiteral();
		//
		// fieldData.add(literal.getLexicalForm());
		//
		// } else if (node.isResource()) {
		// Resource resource = node.asResource();
		//
		// fieldData.add(resource.toString());
		// }
	}
}
