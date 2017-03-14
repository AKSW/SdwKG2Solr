package sdw.aksw.org.sparql;

import java.util.ArrayList;
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
public class AddrMapping2 implements Solr2SparqlMappingInterface {

	static final Pattern bracketsPattern = Pattern.compile("\\(.*?\\)");

	static final Pattern dbpediapattern = Pattern.compile("[\\d\\.,\\s-]+");
	
	final static protected Pattern coordinatesPattern = Pattern.compile("(?<=POINT\\()([0-9.]+)(\\s+)([0-9.]+)");

	@Override
	public void fillFieldDataMap(final QuerySolution querySolution, final KgSolrMapping mapping,
			final String matchingVarName, final String solrFieldName, final Map<String, Set<String>> fieldDataMap) {

		String kgVar = mapping.kgVariableName;
		JsonObject jo = new JsonObject();

		int index;

		//TODO check index + kgvar already used ?

		// Loc+ Straße+ Plz+ Land+ Bland+ Stadt+

		if (matchingVarName.contains(kgVar + "Land")) {
			index = Integer.parseInt(matchingVarName.substring((kgVar + "1234").length()));
		} else if (matchingVarName.contains(kgVar + "Loc") || matchingVarName.contains(kgVar + "Plz")) {
			index = Integer.parseInt(matchingVarName.substring((kgVar + "123").length()));
			
		} else if (matchingVarName.contains(kgVar + "Strasse")) {
			index = Integer.parseInt(matchingVarName.substring((kgVar + "1234567").length()));
			
		} else if (matchingVarName.contains(kgVar + "Bland") || matchingVarName.contains(kgVar + "Stadt")) {
			index = Integer.parseInt(matchingVarName.substring((kgVar + "12345").length()));
			
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

		if (uri_str.startsWith("http")) {

			jo.addProperty("uri", uri_str);
		}
	
		// name Straße+ Plz+ Land+ Bland+ Stadt+
		RDFNode straße = querySolution.get(kgVar + "Strasse" + index);
		String straße_str = "";
		String straße_lit = "";

		RDFNode plz = querySolution.get(kgVar + "Plz" + index);
		String plz_str = "";
		String plz_lit = "";
		
		RDFNode land = querySolution.get(kgVar + "Land" + index);
		String land_str = "";
		String land_lit = "";
		
		RDFNode bland = querySolution.get(kgVar + "Bland" + index);
		String bland_str = "";
		String bland_lit = "";
		
		RDFNode stadt = querySolution.get(kgVar + "Stadt" + index);
		String stadt_str = "";
		String stadt_lit = "";
		
		String name ="";

		if (true) {
			
			//Marvin Hofer [4:53 PM] 
			//Straße Plz Stadt Bland Land

			if (null != straße) {
				if (straße.isLiteral()) {
					straße_str += straße.asLiteral().getLexicalForm();
					straße_lit += straße.asLiteral();

				} else if (straße.isResource()) {
					straße_str += straße.asResource().toString();
				}
			}
			
			if (null != plz) {
				if (plz.isLiteral()) {
					plz_str += plz.asLiteral().getLexicalForm();
					plz_lit += plz.asLiteral();

				} else if (plz.isResource()) {
					plz_str += plz.asResource().toString();
				}
				
			}
			
			if (null != stadt) {
				if (stadt.isLiteral()) {
					stadt_str += stadt.asLiteral().getLexicalForm();
					stadt_lit += stadt.asLiteral();

				} else if (stadt.isResource()) {
					stadt_str += stadt.asResource().toString();
				}
				
			}
			
			if (null != bland) {
				if (bland.isLiteral()) {
					bland_str += bland.asLiteral().getLexicalForm();
					bland_lit += bland.asLiteral();

					
				} else if (bland.isResource()) {
					bland_str += bland.asResource().toString();
				}
				
			}
			
			if (null != land) {
				if (land.isLiteral()) {
					land_str += land.asLiteral().getLexicalForm();
					land_lit += land.asLiteral();

				} else if (land.isResource()) {
					land_str += land.asResource().toString();
				}
				
			}
			
			//Only de and en

			String[] lang = {"@de","@en"};

			for (String l : lang ) {
				if (  
					( stadt_lit.contains(l) || stadt_lit.equals("") ) && 
					( bland_lit.contains(l) || bland_lit.equals("") ) && 
					( land_lit.contains(l) || stadt_lit.equals("") ) 
				) {
				
					ArrayList<String> list_name = new ArrayList<String>();
					
					if( !straße_str.equals("") ) {
						list_name.add(straße_str); }
					if( !plz_str.equals("") ) {
						list_name.add(plz_str); }
					if( !stadt_str.equals("") ) {
						list_name.add(stadt_str); }
					if( !bland_str.equals("") ) {
						list_name.add(bland_str); }
					if( !land_str.equals("") ) {
						list_name.add(land_str);
					}
					name = String.join(",", list_name);
				}	
			}

			//name = straße_lit+", "+plz_lit+", "+stadt_lit+", "+bland_lit+", "+land_lit;
			
			if( !name.equals("") ) {
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
			
				fieldData.add(name);

				jo.addProperty("name", name);
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
		
		if (null == coordinateArray || 2 != coordinateArray.length) {
			return;
		}
		
		//Coordfields -180 < X < 180 & -90 < Y 90

		//float x = Float.parseFloat(coordinateArray[0]);
		//float y = Float.parseFloat(coordinateArray[1]);

		//if( !(-180 < x && x < 180 ) || !( -90 < y && y < 90 ) ) {
		//	return;
		//}
	
		//For google Y,X save
		jo.addProperty("location", coordinateArray[1]+","+coordinateArray[0]);

		//JSON
		if( !name.equals("") ) {
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

		//Solr LatLon and Rpt
		if (true)

		{

			boolean st_location = true;
			
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
				 st_location = false;
			 }
			
			 if(matchingVarName.startsWith("headquarter")) {
				 nameLatLon = "headquarterLocationLatLon";
				 nameRpt = "headquarterLocationRpt";
				 st_location = false;
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
