package org.matsim.contrib.drt.optimizer.rebalancing.demandestimator;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.Triple;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.drt.analysis.zonal.DrtZonalSystem;
import org.matsim.contrib.drt.analysis.zonal.DrtZone;
import org.matsim.contrib.drt.optimizer.rebalancing.Feedforward.FeedforwardRebalancingStrategyParams;
import org.matsim.contrib.drt.passenger.events.DrtRequestSubmittedEvent;
import org.matsim.contrib.drt.passenger.events.DrtRequestSubmittedEventHandler;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.dvrp.passenger.PassengerRequestRejectedEvent;
import org.matsim.contrib.dvrp.passenger.PassengerRequestRejectedEventHandler;
import org.matsim.contrib.dvrp.passenger.PassengerRequestScheduledEvent;
import org.matsim.contrib.dvrp.passenger.PassengerRequestScheduledEventHandler;

public class NetDepartureReplenishDemandEstimator implements ZonalDemandEstimator,
		PassengerRequestScheduledEventHandler, DrtRequestSubmittedEventHandler, PassengerRequestRejectedEventHandler {

	private final DrtZonalSystem zonalSystem;
	private final String mode;
	private final int timeBinSize;
	private final Map<Double, Map<DrtZone, MutableInt>> currentZoneNetDepartureMap = new HashMap<>();
	private final Map<Double, Map<DrtZone, MutableInt>> previousZoneNetDepartureMap = new HashMap<>();
	private final Map<Id<Person>, Triple<Double, DrtZone, DrtZone>> potentialDRTTripsMap = new HashMap<>();
	private static final MutableInt ZERO = new MutableInt(0);
	private final int simulationEndTime = 36; // simulation ending time in hour (greater than or equal to actual end
												// time)

	public NetDepartureReplenishDemandEstimator(DrtZonalSystem zonalSystem, DrtConfigGroup drtCfg,
			FeedforwardRebalancingStrategyParams strategySpecificParams) {
		this.zonalSystem = zonalSystem;
		mode = drtCfg.getMode();
		timeBinSize = strategySpecificParams.getTimeBinSize();

	}

	@Override
	public void handleEvent(PassengerRequestRejectedEvent event) {
		// If a request is rejected, remove the request info from the temporary storage
		// place
		Id<Person> personId = event.getPersonId();
		potentialDRTTripsMap.remove(personId);
	}

	@Override
	public void handleEvent(DrtRequestSubmittedEvent event) {
		// Here, we get a potential DRT trip. We will first note it down in the
		// temporary data base (Potential DRT Trips Map)
		if (event.getMode().equals(mode)) {
			Id<Person> personId = event.getPersonId();
			double timeBin = Math.floor(event.getTime() / timeBinSize);
			DrtZone departureZoneId = zonalSystem.getZoneForLinkId(event.getFromLinkId());
			DrtZone arrivalZoneId = zonalSystem.getZoneForLinkId(event.getToLinkId());
			potentialDRTTripsMap.put(personId, Triple.of(timeBin, departureZoneId, arrivalZoneId));
		}
	}

	@Override
	public void handleEvent(PassengerRequestScheduledEvent event) {
		// When the request is scheduled (i.e. accepted), add this travel information to
		// the database;
		// Then remove the travel information from the potential trips Map
		if (event.getMode().equals(mode)) {
			Id<Person> personId = event.getPersonId();
			double timeBin = potentialDRTTripsMap.get(personId).getLeft();
			DrtZone departureZone = potentialDRTTripsMap.get(personId).getMiddle();
			DrtZone arrivalZone = potentialDRTTripsMap.get(personId).getRight();

			currentZoneNetDepartureMap.get(timeBin).get(departureZone).increment();
			currentZoneNetDepartureMap.get(timeBin).get(arrivalZone).decrement();
			potentialDRTTripsMap.remove(personId);
		}
	}

	@Override
	public void reset(int iteration) {
		previousZoneNetDepartureMap.clear();
		previousZoneNetDepartureMap.putAll(currentZoneNetDepartureMap);
		prepareZoneNetDepartureMap();
	}

	@Override
	public ToDoubleFunction<DrtZone> getExpectedDemandForTimeBin(double time) {
		double timeBin = Math.floor(time / timeBinSize);
		Map<DrtZone, MutableInt> expectedDemandForTimeBin = previousZoneNetDepartureMap.getOrDefault(timeBin,
				Collections.emptyMap());
		return zone -> expectedDemandForTimeBin.getOrDefault(zone, ZERO).intValue();
	}

	private void prepareZoneNetDepartureMap() {
		for (int i = 0; i < (3600 / timeBinSize) * simulationEndTime; i++) {
			Map<DrtZone, MutableInt> zonesPerSlot = new HashMap<>();
			for (DrtZone zone : zonalSystem.getZones().values()) {
				zonesPerSlot.put(zone, new MutableInt());
			}
			currentZoneNetDepartureMap.put((double) i, zonesPerSlot);
		}
	}

}
