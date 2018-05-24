package ch.ethz.matsim.baseline_scenario.transit.routing;

import java.util.LinkedList;
import java.util.List;

import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.core.router.StageActivityTypesImpl;
import org.matsim.core.router.Transit;
import org.matsim.facilities.Facility;
import org.matsim.pt.PtConstants;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class BaselineTransitRoutingModule implements RoutingModule {
	final private EnrichedTransitRouter transitRouter;
	final private TransitSchedule transitSchedule;

	public BaselineTransitRoutingModule(EnrichedTransitRouter transitRouter, TransitSchedule transitSchedule) {
		this.transitRouter = transitRouter;
		this.transitSchedule = transitSchedule;
	}

	@Override
	public List<? extends PlanElement> calcRoute(Facility<?> fromFacility, Facility<?> toFacility, double departureTime,
			Person person) {
		List<Leg> legs = transitRouter.calculateRoute(fromFacility, toFacility, departureTime, person);
		List<PlanElement> trip = new LinkedList<>();

		Facility<?> currentFacility = fromFacility;

		for (Leg leg : legs.subList(0, legs.size() - 1)) {
			trip.add(leg);

			Activity activity = PopulationUtils.createActivityFromCoordAndLinkId(PtConstants.TRANSIT_ACTIVITY_TYPE,
					currentFacility.getCoord(), currentFacility.getLinkId());

			activity.setMaximumDuration(0.0);
			trip.add(activity);

			if (leg.getMode().equals("pt")) {
				EnrichedTransitRoute route = (EnrichedTransitRoute) leg.getRoute();
				currentFacility = transitSchedule.getFacilities().get(route.getEgressStopId());
			}
		}

		trip.add(legs.get(legs.size() - 1));

		return trip;
	}

	@Override
	public StageActivityTypes getStageActivityTypes() {
		return new StageActivityTypesImpl(PtConstants.TRANSIT_ACTIVITY_TYPE);
	}
}
