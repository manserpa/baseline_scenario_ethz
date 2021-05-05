package ch.ethz.matsim.baseline_scenario.analysis.ptOperator.listeners;

import ch.ethz.matsim.baseline_scenario.analysis.ptOperator.VehicleDepartureItem;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.*;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.handler.VehicleArrivesAtFacilityEventHandler;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.Vehicles;

import java.util.*;

public class VehicleDepartureListener implements TransitDriverStartsEventHandler, VehicleLeavesTrafficEventHandler,
        LinkLeaveEventHandler, VehicleArrivesAtFacilityEventHandler, PersonLeavesVehicleEventHandler,
        PersonEntersVehicleEventHandler {

    final private Network network;
    private final HashMap<Id<TransitLine>, Set<Id<TransitRoute>>> removedLines;

    final private List<VehicleDepartureItem> departures = new LinkedList<>();

    final private Map<Id<Vehicle>, VehicleDepartureItem> ongoing = new HashMap<>();
    final private Set<Id<Person>> ptDrivers = new HashSet<>();

    private final Set<Id<Link>> uniqueLinks = new HashSet<>();
    private final Set<Id<TransitStopFacility>> uniqueStops = new HashSet<>();
    private final Set<Id<Link>> linksInArea;
    private final Set<Id<TransitStopFacility>> stopsInArea;
    private final Vehicles vehicles;

    public VehicleDepartureListener(Network network, Vehicles transitVehicles, HashMap<Id<TransitLine>,
                                    Set<Id<TransitRoute>>> removedLines, Set<Id<Link>> linksInArea,
                                    Set<Id<TransitStopFacility>> stopsInArea) {
        this.network = network;
        this.vehicles = transitVehicles;
        this.removedLines = removedLines;
        this.linksInArea = linksInArea;
        this.stopsInArea = stopsInArea;
    }

    @Override
    public void handleEvent(TransitDriverStartsEvent event)	{
        if(event.getVehicleId().toString().contains("para"))	{
            VehicleDepartureItem item = new VehicleDepartureItem(event.getTransitLineId(),
                    event.getTransitRouteId(), event.getDepartureId());
            item.lineId = event.getTransitLineId();
            item.routeId = event.getTransitRouteId();
            item.departureId = event.getDepartureId();
            item.departureTime = event.getTime();

            Vehicle veh = this.vehicles.getVehicles().get(event.getVehicleId());
            item.capacity = 6 * (veh.getType().getCapacity().getStandingRoom() +
                    veh.getType().getCapacity().getSeats() - 1);

            ongoing.put(event.getVehicleId(), item);
        }
        if(removedLines.containsKey(event.getTransitLineId()))	{
            if(removedLines.get(event.getTransitLineId()).contains(event.getTransitRouteId()))	{
                VehicleDepartureItem item = new VehicleDepartureItem(event.getTransitLineId(),
                        event.getTransitRouteId(), event.getDepartureId());
                item.lineId = event.getTransitLineId();
                item.routeId = event.getTransitRouteId();
                item.departureId = event.getDepartureId();
                item.departureTime = event.getTime();

                Vehicle veh = this.vehicles.getVehicles().get(event.getVehicleId());
                item.capacity = veh.getType().getCapacity().getStandingRoom() +
                        veh.getType().getCapacity().getSeats();

                ongoing.put(event.getVehicleId(), item);
            }
        }

        ptDrivers.add(event.getDriverId());
    }

    @Override
    public void handleEvent(LinkLeaveEvent event)	{
        VehicleDepartureItem item = ongoing.get(event.getVehicleId());
        if(item != null && linksInArea.contains(event.getLinkId())) {
            double linkLength = network.getLinks().get(event.getLinkId()).getLength();
            item.incVehAndPaxDistance(linkLength);
            uniqueLinks.add(event.getLinkId());
        }
    }

    @Override
    public void handleEvent(VehicleArrivesAtFacilityEvent event)	{
        VehicleDepartureItem item = ongoing.get(event.getVehicleId());
        if(item != null && stopsInArea.contains(event.getFacilityId())) {
            item.noStops++;
            uniqueStops.add(event.getFacilityId());
            item.setCurrentStop(event.getFacilityId());
        }
    }

    @Override
    public void handleEvent(PersonEntersVehicleEvent event) {
        VehicleDepartureItem item = ongoing.get(event.getVehicleId());
        if(item != null && !ptDrivers.contains(event.getPersonId()) && stopsInArea.contains(item.currentStop)) {
            item.addPassenger(event.getPersonId());
            item.passengerBoardings++;
        }
    }

    @Override
    public void handleEvent(PersonLeavesVehicleEvent event) {
        VehicleDepartureItem item = ongoing.get(event.getVehicleId());
        if(item != null && !ptDrivers.contains(event.getPersonId())) {
            item.removePassenger(event.getPersonId());
        }
    }

    @Override
    public void handleEvent(VehicleLeavesTrafficEvent event)	{
        VehicleDepartureItem item = ongoing.remove(event.getVehicleId());
        if (item != null) {
            item.vehicleTime = event.getTime() - item.departureTime;
            departures.add(item);
        }

        if(ptDrivers.contains(event.getPersonId()))	{
            ptDrivers.remove(event.getPersonId());
        }
    }

    public Collection<VehicleDepartureItem> getVehicleDepartureItems() {
        double networkLength = 0.0;
        for(Id<Link> link: this.uniqueLinks)    {
            networkLength += network.getLinks().get(link).getLength();
        }
        for(VehicleDepartureItem item: departures)  {
            item.totStops = this.uniqueStops.size();
            item.networkLength = networkLength;
        }
        return departures;
    }
}
