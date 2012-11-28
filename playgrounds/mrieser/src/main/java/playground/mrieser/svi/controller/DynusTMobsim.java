/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.mrieser.svi.controller;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.Controler;
import org.matsim.core.mobsim.framework.Mobsim;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.core.utils.io.IOUtils;

import playground.mrieser.svi.data.DynamicODMatrix;
import playground.mrieser.svi.data.DynusTDynamicODDemandWriter;
import playground.mrieser.svi.data.vehtrajectories.CalculateLinkStatsFromVehTrajectories;
import playground.mrieser.svi.data.vehtrajectories.CalculateLinkTravelTimesFromVehTrajectories;
import playground.mrieser.svi.data.vehtrajectories.CalculateTravelTimeMatrixFromVehTrajectories;
import playground.mrieser.svi.data.vehtrajectories.DynamicTravelTimeMatrix;
import playground.mrieser.svi.data.vehtrajectories.MultipleVehicleTrajectoryHandler;
import playground.mrieser.svi.data.vehtrajectories.VehicleTrajectoriesReader;
import playground.mrieser.svi.replanning.DynamicODDemandCollector;

/**
 * @author mrieser
 */
public class DynusTMobsim implements Mobsim {

	private final static Logger log = Logger.getLogger(DynusTMobsim.class);

	private final DynusTConfig dc;
	private final Scenario scenario;
	private final DynamicTravelTimeMatrix ttMatrix;
	private final Network dynusTnet;
	private final Controler controler;

	public DynusTMobsim(final DynusTConfig dc, final DynamicTravelTimeMatrix ttMatrix, final Scenario sc, final EventsManager eventsManager,
			final Network dynusTnet, final Controler controler) {
		this.dc = dc;
		this.scenario = sc;
		this.ttMatrix = ttMatrix;
		this.dynusTnet = dynusTnet;
		this.controler = controler;
	}

	@Override
	public void run() {
		// prepare matrix
		log.info("collect demand for Dynus-T");
		DynamicODMatrix odm = new DynamicODMatrix(this.dc.getTimeBinSize_min()*60, 24*60*60);
		DynamicODDemandCollector collector = new DynamicODDemandCollector(odm, this.dc.getActToZoneMapping());

		for (Person person : this.scenario.getPopulation().getPersons().values()) {
			Plan plan = person.getSelectedPlan();
			collector.run(plan);
		}
		log.info("Number of trips handed over to DynusT: " + collector.getCounter());
		printModeShares(collector);
		
		log.info("write marginal sums");
		exportMarginalSums(odm, this.controler.getControlerIO().getIterationFilename(this.controler.getIterationNumber(), "nachfrageRandsummen.txt"));
		
		log.info("write demand for Dynus-T with factor " + this.dc.getDemandFactor());
		DynusTDynamicODDemandWriter writer = new DynusTDynamicODDemandWriter(odm, this.dc.getZoneIdToIndexMapping());
		writer.setMultiplyFactor(this.dc.getDemandFactor());
		writer.writeTo(this.dc.getOutputDirectory() + "/demand.dat");

		// run DynusT
		log.info("run Dynus-T");
		DynusTExe exe = new DynusTExe(this.dc.getDynusTDirectory(), this.dc.getModelDirectory(), this.dc.getOutputDirectory());
		exe.runDynusT(true);

		// read in data, convert it somehow to score the plans
		log.info("read in Vehicle Trajectories from DynusT");
		String vehTrajFilename = this.dc.getOutputDirectory() + "/VehTrajectory.dat";
		
		MultipleVehicleTrajectoryHandler multiHandler = new MultipleVehicleTrajectoryHandler();
		CalculateTravelTimeMatrixFromVehTrajectories ttmCalc = new CalculateTravelTimeMatrixFromVehTrajectories(this.ttMatrix);
		multiHandler.addTrajectoryHandler(ttmCalc);
		TravelTimeCalculator ttc = new TravelTimeCalculator(this.dynusTnet, this.scenario.getConfig().travelTimeCalculator());
		CalculateLinkTravelTimesFromVehTrajectories lttCalc = new CalculateLinkTravelTimesFromVehTrajectories(ttc, this.dynusTnet);
		multiHandler.addTrajectoryHandler(lttCalc);
		CalculateLinkStatsFromVehTrajectories linkStats = new CalculateLinkStatsFromVehTrajectories(this.dynusTnet);
		multiHandler.addTrajectoryHandler(linkStats);
		
		new VehicleTrajectoriesReader(multiHandler, this.dc.getZoneIdToIndexMapping()).readFile(vehTrajFilename);

		this.dc.setTravelTimeCalculator(ttc);
		linkStats.writeLinkVolumesToFile(this.controler.getControlerIO().getIterationFilename(this.controler.getIterationNumber(), "dynust_linkVolumes.txt"));
		linkStats.writeLinkTravelTimesToFile(this.controler.getControlerIO().getIterationFilename(this.controler.getIterationNumber(), "dynust_linkTravelTimes.txt"));
		linkStats.writeLinkTravelSpeedsToFile(this.controler.getControlerIO().getIterationFilename(this.controler.getIterationNumber(), "dynust_linkTravelSpeeds.txt"));
	}
	
	private void printModeShares(final DynamicODDemandCollector collector) {
		log.info("Mode share statistics:");
		int sum = 0;
		for (Map.Entry<String, Integer> e : collector.getModeCounts().entrySet()) {
			sum += e.getValue().intValue();
		}
		for (Map.Entry<String, Integer> e : collector.getModeCounts().entrySet()) {
			log.info("   # trips with mode " + e.getKey() + " = " + e.getValue() + " (" + ((e.getValue().doubleValue() / (double) sum) * 100) + "%)");
		}		
	}
	
	private void exportMarginalSums(final DynamicODMatrix matrix, final String filename) {
		Map<String, Integer> origins = new HashMap<String, Integer>();
		Map<String, Integer> destinations = new HashMap<String, Integer>();
		
		for (int i = 0; i < matrix.getNOfBins(); i++) {
			Map<String, Map<String, Integer>> timeMatrix = matrix.getMatrixForTimeBin(i);
			
			for (Map.Entry<String, Map<String, Integer>> fromZone : timeMatrix.entrySet()) {
				String fromZoneId = fromZone.getKey();
				for (Map.Entry<String, Integer> toZone : fromZone.getValue().entrySet()) {
					String toZoneId = toZone.getKey();
					Integer volume = toZone.getValue();
					
					Integer origVol = origins.get(fromZoneId);
					if (origVol == null) {
						origins.put(fromZoneId, volume);
					} else {
						origins.put(fromZoneId, volume.intValue() + origVol.intValue());
					}

					Integer destVol = destinations.get(toZoneId);
					if (destVol == null) {
						destinations.put(toZoneId, volume);
					} else {
						destinations.put(toZoneId, volume.intValue() + destVol.intValue());
					}
				}
			}
		}

		Set<String> zoneIds = new HashSet<String>(origins.keySet());
		zoneIds.addAll(destinations.keySet());
		BufferedWriter writer = IOUtils.getBufferedWriter(filename);

		try {
			writer.write("ZONE\tQuellverkehr\rZielverkehr" + IOUtils.NATIVE_NEWLINE);
			
			for (String id : zoneIds) {
				writer.write(id + "\t");
				Integer vol = origins.get(id);
				if (vol != null) {
					writer.write(vol.toString());
				}
				writer.write("\t");
				vol = destinations.get(id);
				if (vol != null) {
					writer.write(vol.toString());
				}
				writer.write(IOUtils.NATIVE_NEWLINE);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
