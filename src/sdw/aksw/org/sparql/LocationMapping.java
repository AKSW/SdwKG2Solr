package sdw.aksw.org.sparql;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
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
public class LocationMapping implements Solr2SparqlMappingInterface {

	static final Pattern bracketsPattern = Pattern.compile("\\(.*?\\)");

	static final Pattern pattern = Pattern.compile("[\\d\\.,\\s-]+");

	@Override
	public void fillFieldDataMap(final QuerySolution querySolution, final KgSolrMapping mapping,
			final String matchingVarName, final String solrFieldName, final Map<String, Set<String>> fieldDataMap) {

		String kgVar = mapping.kgVariableName;
		JsonObject jo = new JsonObject();

		int index = 0;
		
		//Loc+ Straße Plz+ Land+ Bland Stadt
		

		if (matchingVarName.contains(kgVar + "Name")) {
			index = Integer.parseInt(matchingVarName.substring((kgVar + "Name").length()));
		} else if (matchingVarName.contains(kgVar + "Loc") || matchingVarName.contains(kgVar + "Plz")) {

			index = Integer.parseInt(matchingVarName.substring( (kgVar + "###").length()) );
		}
		else {
			index = Integer.parseInt(matchingVarName.substring(kgVar.length()));

		}

		// uri
		RDFNode uri = querySolution.get(kgVar + index);
		if (null == uri) {
			return;
		}

		String uri_str = "";

		if (uri.isLiteral()) {
			uri_str += uri.asLiteral().getLexicalForm().toString();
		} else if (uri.isResource()) {
			uri_str += uri.asResource().toString();
		}

		if (uri_str.startsWith("http"))

			jo.addProperty("uri", uri_str);

		// name
		RDFNode name = querySolution.get(kgVar + "Name" + index);
		String name_str = "";

		if (mapping.matches(name, 0)) {

			if (null != name) {
				if (name.isLiteral()) {
					name_str += bracketsPattern.matcher(name.asLiteral().getLexicalForm()).replaceAll("");
					;

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
		}

		// Loc
		RDFNode loc = querySolution.get(kgVar + "Loc" + index);
		String loc_str = "";

		if (null != loc) {
			if (loc.isLiteral()) {
				loc_str += loc.asLiteral().getLexicalForm().toString();
			} else if (loc.isResource()) {
				loc_str += loc.asResource().toString();
			}
		}
				
		Matcher match = pattern.matcher(loc_str);

		if (match.matches() && !loc_str.equals("")) {
			jo.addProperty("location", loc_str.replace(" ", ","));
		}

		//JSON
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

		
		//Coordfields

		// System.out.println(lat_str+","+lon_str);

		if (match.matches() && !loc_str.equals(""))

		{

			boolean st_location = true;

			String nameLatLon = "locationLatLon";
			String nameRpt = "locationRpt";

			String[] loc_arr = loc_str.split(" ");

			String latitude = loc_arr[1];
			String longitude = loc_arr[0];
			String latLong = latitude + "," + longitude;
			String rpt = longitude + " " + latitude;

			Set<String> coordinateSet0 = null;
			Set<String> coordinateSet1 = null;

			lock.lock();
			try {
				coordinateSet0 = fieldDataMap.get(nameLatLon);
				if (null == coordinateSet0) {
					coordinateSet0 = new ConcurrentHashSet<>();
					fieldDataMap.put(nameLatLon, coordinateSet0);
				}

				coordinateSet1 = fieldDataMap.get(nameRpt);
				if (null == coordinateSet1) {
					coordinateSet1 = new ConcurrentHashSet<>();
					fieldDataMap.put(nameRpt, coordinateSet1);
				}
			} finally {
				lock.unlock();
			}

			coordinateSet0.add(latLong);
			coordinateSet1.add(rpt);

			if (matchingVarName.startsWith("foundationPlace")) {
				nameLatLon = "foundationPlaceLatLon";
				nameRpt = "foundationPlaceRpt";
				st_location = false;
			}

			if (matchingVarName.startsWith("headquarter")) {
				nameLatLon = "headquarterLocationLatLon";
				nameRpt = "headquarterLocationRpt";
				st_location = false;
			}

			if (false == st_location) {

				Set<String> coordinateSet3 = null;
				Set<String> coordinateSet4 = null;

				lock.lock();
				try {
					coordinateSet3 = fieldDataMap.get(nameLatLon);
					if (null == coordinateSet3) {
						coordinateSet3 = new ConcurrentHashSet<>();
						fieldDataMap.put(nameLatLon, coordinateSet3);
					}

					coordinateSet4 = fieldDataMap.get(nameRpt);
					if (null == coordinateSet4) {
						coordinateSet4 = new ConcurrentHashSet<>();
						fieldDataMap.put(nameRpt, coordinateSet4);
					}
				} finally {
					lock.unlock();
				}

				coordinateSet3.add(latLong);
				coordinateSet4.add(rpt);
			}
		}
	}
}
