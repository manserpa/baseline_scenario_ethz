package ch.ethz.matsim.baseline_scenario.transit.simulation;

import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonStuckEvent;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.TeleportationArrivalEvent;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.PlanAgent;
import org.matsim.core.mobsim.qsim.InternalInterface;
import org.matsim.core.mobsim.qsim.interfaces.AgentCounter;
import org.matsim.core.mobsim.qsim.interfaces.DepartureHandler;
import org.matsim.core.mobsim.qsim.interfaces.MobsimEngine;
import org.matsim.pt.transitSchedule.api.*;

import com.google.inject.Singleton;

import ch.ethz.matsim.baseline_scenario.transit.events.PublicTransitEvent;
import ch.ethz.matsim.baseline_scenario.transit.routing.EnrichedTransitRoute;
import ch.ethz.matsim.baseline_scenario.zurich.cutter.utils.DepartureFinder;
import ch.ethz.matsim.baseline_scenario.zurich.cutter.utils.DepartureFinder.NoDepartureFoundException;

@Singleton
public class BaselineTransitEngine implements DepartureHandler, MobsimEngine {
	final private TransitSchedule transitSchedule;
	final private DepartureFinder departureFinder;
	private InternalInterface internalInterface;
	final private EventsManager eventsManager;
	final private AgentCounter agentCounter;

	final private PriorityQueue<AgentDeparture> departures = new PriorityQueue<>();
	final private PriorityQueue<AgentArrival> arrivals = new PriorityQueue<>();

	private final static Logger log = Logger.getLogger(BaselineTransitEngine.class);

	private class AgentDeparture implements Comparable<AgentDeparture> {
		final public MobsimAgent agent;
		final public double departureTime;
		final public Id<Link> departureLinkId;

		public AgentDeparture(MobsimAgent agent, double departureTime, Id<Link> departureLinkId) {
			this.agent = agent;
			this.departureTime = departureTime;
			this.departureLinkId = departureLinkId;
		}

		@Override
		public int compareTo(AgentDeparture other) {
			return Double.compare(departureTime, other.departureTime);
		}
	}

	private class AgentArrival implements Comparable<AgentArrival> {
		final public MobsimAgent agent;
		final public double arrivalTime;
		final public Id<Link> arrivalLinkId;
		final public PublicTransitEvent event;

		public AgentArrival(MobsimAgent agent, double arrivalTime, Id<Link> arrivalLinkId, PublicTransitEvent event) {
			this.agent = agent;
			this.arrivalTime = arrivalTime;
			this.arrivalLinkId = arrivalLinkId;
			this.event = event;
		}

		@Override
		public int compareTo(AgentArrival other) {
			return Double.compare(arrivalTime, other.arrivalTime);
		}
	}

	public BaselineTransitEngine(EventsManager eventsManager, TransitSchedule transitSchedule,
			DepartureFinder departureFinder, AgentCounter agentCounter) {
		this.eventsManager = eventsManager;
		this.transitSchedule = transitSchedule;
		this.departureFinder = departureFinder;
		this.agentCounter = agentCounter;
	}

	@Override
	public boolean handleDeparture(double now, MobsimAgent agent, Id<Link> departureLinkId) {
		if (agent.getMode().equals("pt")) {
			log.info("agent departs");
			Leg leg = (Leg) ((PlanAgent) agent).getCurrentPlanElement();
			EnrichedTransitRoute route = (EnrichedTransitRoute) leg.getRoute();

			TransitLine transitLine = transitSchedule.getTransitLines().get(route.getTransitLineId());
			TransitRoute transitRoute = transitLine.getRoutes().get(route.getTransitRouteId());

			TransitStopFacility access = transitSchedule.getFacilities().get(route.getAccessStopId());
			TransitRouteStop accessStop = transitRoute.getStop(access);
			TransitStopFacility egress = transitSchedule.getFacilities().get(route.getEgressStopId());
			TransitRouteStop egressStop = transitRoute.getStop(egress);

			try {
				Departure departure = departureFinder.findDeparture(transitRoute, accessStop, now);

				double inVehicleTime = egressStop.getArrivalOffset() - accessStop.getDepartureOffset();
				double vehicleDepartureTime = departure.getDepartureTime() + accessStop.getDepartureOffset();
				double arrivalTime = vehicleDepartureTime + inVehicleTime;

				if (arrivalTime < vehicleDepartureTime || arrivalTime < now) {
					throw new IllegalStateException();
				}

				if (Math.abs(arrivalTime - now) < 1.0) {
					arrivalTime = now + 1.0;
				}

				Id<Link> arrivalLinkId = egressStop.getStopFacility().getLinkId();

				if (!accessStop.getStopFacility().getLinkId().equals(departureLinkId)) {
					throw new IllegalStateException();
				}

				PublicTransitEvent transitEvent = new PublicTransitEvent(arrivalTime, agent.getId(),
						transitLine.getId(), transitRoute.getId(), accessStop.getStopFacility().getId(),
						egressStop.getStopFacility().getId(), vehicleDepartureTime, route.getDistance());

				internalInterface.registerAdditionalAgentOnLink(agent);
				departures.add(new AgentDeparture(agent, vehicleDepartureTime, departureLinkId));
				arrivals.add(new AgentArrival(agent, arrivalTime, arrivalLinkId, transitEvent));
			} catch (NoDepartureFoundException e) {
				eventsManager.processEvent(new PersonStuckEvent(now, agent.getId(), agent.getCurrentLinkId(), "pt"));
				agentCounter.decLiving();
			}

			return true;
		}

		return false;
	}

	@Override
	public void doSimStep(double time) {
		while (!departures.isEmpty() && departures.peek().departureTime <= time) {
			AgentDeparture departure = departures.poll();
			internalInterface.unregisterAdditionalAgentOnLink(departure.agent.getId(), departure.departureLinkId);
		}

		while (!arrivals.isEmpty() && arrivals.peek().arrivalTime <= time) {
			AgentArrival arrival = arrivals.poll();
			arrival.agent.notifyArrivalOnLinkByNonNetworkMode(arrival.arrivalLinkId);
			eventsManager.processEvent(arrival.event);
			eventsManager.processEvent(new TeleportationArrivalEvent(arrival.arrivalTime, arrival.agent.getId(),
					arrival.event.getTravelDistance()));
			arrival.agent.endLegAndComputeNextState(time);
			internalInterface.arrangeNextAgentState(arrival.agent);
		}
	}

	@Override
	public void onPrepareSim() {
		departures.clear();
		arrivals.clear();
	}

	@Override
	public void afterSim() {
		double time = internalInterface.getMobsim().getSimTimer().getTimeOfDay();
		Set<MobsimAgent> processedAgents = new HashSet<>();

		for (AgentDeparture departure : departures) {
			eventsManager
					.processEvent(new PersonStuckEvent(time, departure.agent.getId(), departure.departureLinkId, "pt"));
			agentCounter.decLiving();
			processedAgents.add(departure.agent);
		}

		for (AgentArrival arrival : arrivals) {
			if (!processedAgents.contains(arrival.agent)) {
				eventsManager
						.processEvent(new PersonStuckEvent(time, arrival.agent.getId(), arrival.arrivalLinkId, "pt"));
				agentCounter.decLiving();
			}
		}
	}

	@Override
	public void setInternalInterface(InternalInterface internalInterface) {
		this.internalInterface = internalInterface;
	}
}
