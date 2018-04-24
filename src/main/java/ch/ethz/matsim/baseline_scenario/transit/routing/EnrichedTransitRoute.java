package ch.ethz.matsim.baseline_scenario.transit.routing;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Route;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public interface EnrichedTransitRoute extends Route {
	double getInVehicleTime();

	double getWaitingTime();

	Id<TransitLine> getTransitLineId();

	Id<TransitRoute> getTransitRouteId();

	Id<Departure> getDepartureId();

	int getAccessStopIndex();

	int getEgressStopIndex();

	Id<TransitStopFacility>  getAccessStopId();

	Id<TransitStopFacility> getEgressStopId();
}
