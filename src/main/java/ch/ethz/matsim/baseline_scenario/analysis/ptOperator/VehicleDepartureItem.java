package ch.ethz.matsim.baseline_scenario.analysis.ptOperator;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VehicleDepartureItem {
    public Id<TransitLine> lineId;
    public Id<TransitRoute> routeId;
    public Id<Departure> departureId;
    public int capacity = 0;

    public int totStops = 0;
    public int noStops = 0;
    public double networkLength = 0.0;

    public double vehicleTime = 0.0;

    public double departureTime = 0.0;

    public int passengerBoardings = 0;

    public Id<TransitStopFacility> currentStop;

    private final Set<Id<Person>> passengers = new HashSet<>();
    public double vehDist = 0.0;
    public double paxDist = 0.0;

    public VehicleDepartureItem(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId, Id<Departure> vehicleId) {
        this.lineId = transitLineId;
        this.routeId = transitRouteId;
        this.departureId = vehicleId;
    }

    public void incVehAndPaxDistance(double linkDistance) {
        paxDist += (linkDistance * passengers.size());
        vehDist += linkDistance;
    }

    public void setCurrentStop(Id<TransitStopFacility> stop)    {
        currentStop = stop;
    }

    public void addPassenger(Id<Person> passengerId) {
        passengers.add(passengerId);
    }

    public void removePassenger(Id passengerId) {
        if(passengers.contains(passengerId))
            passengers.remove(passengerId);
    }
}

