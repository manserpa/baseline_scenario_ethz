package ch.ethz.matsim.baseline_scenario.analysis.ptOperator.run;

import ch.ethz.matsim.baseline_scenario.analysis.ptOperator.CSVVehicleDepartureWriter;
import ch.ethz.matsim.baseline_scenario.analysis.ptOperator.VehicleDepartureItem;
import ch.ethz.matsim.baseline_scenario.analysis.ptOperator.listeners.VehicleDepartureListener;
import ch.ethz.matsim.baseline_scenario.analysis.ptOperator.readers.EventsVehicleDepartureReader;
import ch.ethz.matsim.baseline_scenario.utils.CSVReader;
import com.vividsolutions.jts.geom.*;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.vehicles.VehicleReaderV1;
import org.opengis.feature.simple.SimpleFeature;

import java.io.IOException;
import java.util.*;

public class ConvertVehicleDeparturesFromEvents {

    private final GeometryFactory factory;
    private Geometry include;
    private Geometry exclude;

    static public void main(String[] args) throws IOException {

        String path = "C:\\dev\\ethz\\output\\";

        List<Integer> runList = new ArrayList<>();
        runList.add(1043);
        runList.add(1306);
        runList.add(1323);
        runList.add(1515);
        runList.add(1740);
        runList.add(1744);
        runList.add(1800);
        runList.add(2048);
        runList.add(2193);
        runList.add(2430);

        /*
        // subs
        runList.add(1186);
        runList.add(1381);
        runList.add(1441);
        runList.add(1735);
        runList.add(1866);
        runList.add(2034);
        runList.add(2037);
        runList.add(2123);
        runList.add(1974);
        runList.add(2044);
        */

        for(int run: runList) {
            String fullPath = path + "var_seed_" + run;
            Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
            new MatsimNetworkReader(scenario.getNetwork()).readFile(fullPath + "\\output_network.xml.gz");
            new TransitScheduleReader(scenario).readFile(fullPath + "\\output_transitSchedule.xml.gz");
            new VehicleReaderV1(scenario.getTransitVehicles()).readFile(fullPath + "\\output_transitVehicles.xml.gz");
            // for the reference case...
            HashMap<Id<TransitLine>, Set<Id<TransitRoute>>> removedLines = readRemovedLines(path + "removed_lines.csv");
            ConvertVehicleDeparturesFromEvents ce = new ConvertVehicleDeparturesFromEvents("C:\\dev\\ethz\\perimeter\\OuterScenarioBoundaries.shp");
            Set<Id<Link>> linksInArea = ce.getLinksInArea(scenario.getNetwork());
            Set<Id<TransitStopFacility>> stopsInArea = ce.getStopsInArea(scenario.getTransitSchedule());

            VehicleDepartureListener departureListener = new VehicleDepartureListener(scenario.getNetwork(),
                    scenario.getTransitVehicles(), removedLines, linksInArea, stopsInArea);
            Collection<VehicleDepartureItem> departures = new EventsVehicleDepartureReader(departureListener).
                    readTrips(fullPath + "\\output_events.xml.gz");

            new CSVVehicleDepartureWriter(departures).write(fullPath + "\\analysis\\operator_stats.csv");
        }
    }

    private ConvertVehicleDeparturesFromEvents(String shpFile)	{
        this.factory = new GeometryFactory();
        readShapeFile(shpFile);
    }

    public void readShapeFile(String shpFile) {
        Collection<SimpleFeature> features = new ShapeFileReader().readFileAndInitialize(shpFile);
        Collection<Geometry> include = new ArrayList<>();
        Collection<Geometry> exclude = new ArrayList<>();

        for(SimpleFeature f: features){
            boolean incl = true;
            Geometry g = null;
            for(Object o: f.getAttributes()){
                if(o instanceof Polygon){
                    g = (Geometry) o;
                }else if (o instanceof MultiPolygon){
                    g = (Geometry) o;
                }
                else if (o instanceof String){
                    incl = Boolean.parseBoolean((String) o);
                }
            }
            if(! (g == null)){
                if(incl){
                    include.add(g);
                }else{
                    exclude.add(g);
                }
            }
        }

        this.include = this.factory.createGeometryCollection(include.toArray(new Geometry[include.size()])).buffer(0);
        this.exclude = this.factory.createGeometryCollection(exclude.toArray(new Geometry[exclude.size()])).buffer(0);
    }

    public Set<Id<Link>> getLinksInArea(Network network) {
        Set<Node> nodesInArea = new HashSet<>();
        Set<Id<Link>> linksInArea = new HashSet<>();
        for(Node node: network.getNodes().values()) {
            if(inServiceArea(node.getCoord().getX(), node.getCoord().getY())) {
                nodesInArea.add(node);
            }
        }
        for(Link link: network.getLinks().values()) {
            if(nodesInArea.contains(link.getFromNode()) && nodesInArea.contains(link.getToNode()))  {
                linksInArea.add(link.getId());
            }
        }
        return linksInArea;
    }

    public Set<Id<TransitStopFacility>> getStopsInArea(TransitSchedule schedule)    {
        Set<Id<TransitStopFacility>> stopsInArea = new HashSet<>();
        for(TransitStopFacility stop: schedule.getFacilities().values())    {
            if(inServiceArea(stop.getCoord().getX(), stop.getCoord().getY()))   {
                stopsInArea.add(stop.getId());
            }
        }
        return stopsInArea;
    }

    private boolean inServiceArea(double x, double y) {
        Coordinate coord = new Coordinate(x, y);
        Point p = factory.createPoint(coord);
        if(this.include.contains(p)){
            if(exclude.contains(p)){
                return false;
            }
            return true;
        }
        return false;
    }

    public static HashMap<Id<TransitLine>, Set<Id<TransitRoute>>> readRemovedLines(String path) {
        HashMap<Id<TransitLine>, Set<Id<TransitRoute>>> removedRoutes = new HashMap<>();

        String[] columns = {"line", "route","mode"};
        try (CSVReader reader = new CSVReader(columns, path, ";")) {
            Map<String, String> row = reader.readLine(); // header
            while ((row = reader.readLine()) != null) {

                Id<TransitLine> line = Id.create(row.get("line"), TransitLine.class);
                removedRoutes.putIfAbsent(line, new HashSet<>());

                Id<TransitRoute> route = Id.create(row.get("route"), TransitRoute.class);
                removedRoutes.get(line).add(route);
            }
        }
        catch(IOException ex){
            System.out.println (ex.toString());
        }

        return removedRoutes;
    }
}

