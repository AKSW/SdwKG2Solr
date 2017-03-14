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

	static final Pattern dbpediapattern = Pattern.compile("[\\d\\.,\\s-]+");
	
	final static protected Pattern coordinatesPattern = Pattern.compile("(?<=POINT\\()([0-9.]+)(\\s+)([0-9.]+)");

	@Override
	public void fillFieldDataMap(final QuerySolution querySolution, final KgSolrMapping mapping,
			final String matchingVarName, final String solrFieldName, final Map<String, Set<String>> fieldDataMap) {

		String kgVar = mapping.kgVariableName;
		JsonObject jo = new JsonObject();

		int index;

		if (matchingVarName.contains(kgVar + "Name")) {
			index = Integer.parseInt(matchingVarName.substring((kgVar + "Name").length()));
		} else if (matchingVarName.contains(kgVar + "Lat") || matchingVarName.contains(kgVar + "Lon")) {

			index = Integer.parseInt(matchingVarName.substring((kgVar + "###").length()));

		} else {
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
		

		String coordinatesLiteral = "";

		if (null != loc && loc.isLiteral() ) {	
			coordinatesLiteral = loc.asLiteral().getLexicalForm();
		}

		Matcher match = coordinatesPattern.matcher(coordinatesLiteral);

		String[] coordinateArray = null;
		while (match.find()) {
			int start = match.start();
			int end = match.end();
			
			String coord = coordinatesLiteral.substring(start, end);
			
			//System.out.println(coord);

			coordinateArray = coord.split("\\s+");
			
			
		}
		
		//coordinateArray = coordinatesLiteral.split("\\s+");
		
		if (null == coordinateArray || 2 != coordinateArray.length) {
			return;
		}
		
		jo.addProperty("location", coordinateArray[1]+","+coordinateArray[0]);

		//JSON
		String jsonField = "locationJson";
		
		boolean st_location = true;
		
		//JSON st_location
		if( !name_str.equals("") ) {
			Set<String> fieldData = null;
			lock.lock();
			try {
				fieldData = fieldDataMap.get(jsonField);
	
				if (null == fieldData) {
					fieldData = new ConcurrentHashSet<>();
					fieldDataMap.put(jsonField, fieldData);
				}
			} finally {
				lock.unlock();
			}
	
			StringBuffer buffer = new StringBuffer();
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			gson.toJson(jo, buffer);
	
			fieldData.add(buffer.toString());
			
			//not standart_location
				
			if(matchingVarName.startsWith("foundationPlace")) {
				 jsonField = "foundationPlaceJson";
				 st_location = false;
			 }
			
			 if(matchingVarName.startsWith("headquarter")) {
				 jsonField = "headquarterLocationJson";
				 st_location = false;
			 }

			 if( false == st_location ) {
				 //JSON
				Set<String> fieldData2 = null;
				lock.lock();
				try {
					fieldData2 = fieldDataMap.get(jsonField);
		
					if (null == fieldData2) {
						fieldData2 = new ConcurrentHashSet<>();
						fieldDataMap.put(jsonField, fieldData2);
					}
				} finally {
					lock.unlock();
				}
		
				fieldData2.add(buffer.toString());
				
				 //St_Name
				Set<String> fieldData3 = null;
				lock.lock();
				try {
					fieldData3 = fieldDataMap.get("locationName");
		
					if (null == fieldData3) {
						fieldData3 = new ConcurrentHashSet<>();
						fieldDataMap.put("locationName", fieldData3);
					}
				} finally {
					lock.unlock();
				}
		
				fieldData3.add(name_str);
			 }
		}
		
		
		//Coordfields
		
//		System.out.println(lat_str+","+lon_str);
		
		if (true )

		{
			
			String nameLatLon = "locationLatLon";
			String nameRpt = "locationRpt";
			
			String latitude = coordinateArray[1];
			String longitude = coordinateArray[0];
			String latLong = latitude + "," + longitude;
			String rpt = latitude + "," + longitude;

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

			 if(matchingVarName.startsWith("foundationPlace")) {
				 nameLatLon = "foundationPlaceLatLon";
				 nameRpt = "foundationPlaceRpt";
				 jsonField = "foundationPlaceJson";
			 }
			
			 if(matchingVarName.startsWith("headquarter")) {
				 nameLatLon = "headquarterLocationLatLon";
				 nameRpt = "headquarterLocationRpt";
				 jsonField = "headquarterLocationJson";
			 }

			 if( false == st_location ) {
		
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
