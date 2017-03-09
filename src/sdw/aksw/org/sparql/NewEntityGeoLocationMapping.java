package sdw.aksw.org.sparql;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jetty.util.ConcurrentHashSet;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.rdf.model.RDFNode;

import sdw.aksw.org.config.KgSolrConfig.KgSolrMapping;

public class NewEntityGeoLocationMapping implements Solr2SparqlMappingInterface {

	final static protected Pattern coordinatesPattern = Pattern.compile("(?<=POINT\\()([0-9.]+)(\\s+)([0-9.]+)");

	@Override
	public void fillFieldDataMap(final QuerySolution querySolution, final KgSolrMapping mapping,
			final String matchingVarName, final String solrFieldName, final Map<String, Set<String>> fieldDataMap) {
		RDFNode node = querySolution.get(matchingVarName);
		if (null == node) {
			return;
		}

		if (false == node.isLiteral()) {
			return;
		}

		String coordinatesLiteral = node.asLiteral().getLexicalForm();

		Pattern pattern = Pattern.compile("[\\d\\.,\\s-]+");

		Matcher match = pattern.matcher(coordinatesLiteral);

		if (match.matches()) {

			String nameLatLon = "locationLatLon";
			String nameRpt = "locationRpt";
			
			
			if(matchingVarName.startsWith("fPLocation")) {
				nameLatLon = "foundationPlaceLatLon";			
				nameRpt = "foundationPlaceRpt";
			}
			
			if(matchingVarName.startsWith("hqLocation")) {
				nameLatLon = "headquarterLocationLatLon";			
				nameRpt = "headquarterLocationLatLon";
			}
			
			String[] coordinateArray = coordinatesLiteral.split(" ");

			String latitude = coordinateArray[0];
			String longitude = coordinateArray[1];
			String latLong =  latitude + "," + longitude;
			String rpt = longitude  + " " + latitude;

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
			
		}
	}
}
