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

/**
 * Default mapping class
 * 
 * @author kay
 *
 */
public class EventMapping implements Solr2SparqlMappingInterface {
	
	static final Pattern bracketsPattern = Pattern.compile("\\(.*?\\)");
	
	@Override
	public void fillFieldDataMap(final QuerySolution querySolution, final KgSolrMapping mapping,
								 final String matchingVarName, final String solrFieldName,
								 final Map<String, Set<String>> fieldDataMap) {
		
		String kgVar = mapping.kgVariableName;
		JsonObject jo = new JsonObject();

		int index;
		
		if (matchingVarName.contains(kgVar + "CompanyName")) {
			index = Integer.parseInt(matchingVarName.substring((kgVar + "CompanyName").length()));
		} else if (matchingVarName.contains(kgVar + "Date") || matchingVarName.contains(kgVar + "Plz")) {
			index = Integer.parseInt(matchingVarName.substring((kgVar + "Date").length()));
			
		} else if (matchingVarName.contains(kgVar + "SameAs")) {
			index = Integer.parseInt(matchingVarName.substring((kgVar + "SameAs").length()));
			
		} else {
			index = Integer.parseInt(matchingVarName.substring(kgVar.length()));
		}
		
		RDFNode uri = querySolution.get(kgVar + index);
		if (null == uri) {
			return;
		}

		String date_str = "";
		String companyName_str = "";
		String sameAs_str = "";
		
		RDFNode date = querySolution.get(kgVar + "Date" + index);
		
		if (null != date && date.isLiteral()) {
			
			date_str = date.asLiteral().getLexicalForm().toString();
		
			jo.addProperty("date", date_str);
		} 
	
		
		JsonObject involved = new JsonObject();
		
		RDFNode companyName = querySolution.get(kgVar + "CompanyName" + index);
		
		if (null != companyName && companyName.isLiteral()) {
			companyName_str = companyName.asLiteral().getLexicalForm().toString();
			
			involved.addProperty("name", companyName_str);
		}
		
		RDFNode sameAs = querySolution.get(kgVar + "SameAs" + index);
		
		if (null != sameAs && sameAs.isLiteral()) {
			sameAs_str = sameAs.asLiteral().getLexicalForm().toString();
			
			involved.addProperty("sameAs", sameAs_str);
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
			
		jo.addProperty("involvedCompany", involved.toString());
		
		StringBuffer buffer = new StringBuffer();
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        gson.toJson(jo, buffer);
		
		fieldData.add(buffer.toString());
	}	
}
