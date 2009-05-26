/* *********************************************************************** *
 * project: org.matsim.*
 * AccessEgressDemo.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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

package playground.marcel.pt.demo;

import java.util.ArrayList;

import org.matsim.api.basic.v01.Id;
import org.matsim.api.basic.v01.TransportMode;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.ScenarioImpl;
import org.matsim.core.api.network.Link;
import org.matsim.core.api.network.Node;
import org.matsim.core.api.population.Activity;
import org.matsim.core.api.population.Leg;
import org.matsim.core.api.population.NetworkRoute;
import org.matsim.core.api.population.Person;
import org.matsim.core.api.population.Plan;
import org.matsim.core.api.population.Population;
import org.matsim.core.api.population.PopulationBuilder;
import org.matsim.core.config.Config;
import org.matsim.core.events.Events;
import org.matsim.core.network.NetworkLayer;
import org.matsim.transitSchedule.TransitStopFacility;

import playground.marcel.OTFDemo;
import playground.marcel.pt.analysis.TransitRouteAccessEgressAnalysis;
import playground.marcel.pt.analysis.VehicleTracker;
import playground.marcel.pt.integration.ExperimentalTransitRoute;
import playground.marcel.pt.integration.TransitQueueSimulation;
import playground.marcel.pt.transitSchedule.Departure;
import playground.marcel.pt.transitSchedule.TransitLine;
import playground.marcel.pt.transitSchedule.TransitRoute;
import playground.marcel.pt.transitSchedule.TransitRouteStop;
import playground.marcel.pt.transitSchedule.TransitSchedule;

public class AccessEgressDemo {

	private static final int nOfLinks = 15;
	private static final int nOfBuses = 20;
	private static final int nOfAgentsPerStop = 100;
	private static final int agentInterval = 60;
	private static final int delayedBus = 6;
	private static final int heading = 5*60;
	private static final int delay = 60;
	private static final double departureTime = 7.0*3600;
	
	private Scenario scenario = new ScenarioImpl();
	private TransitSchedule schedule = new TransitSchedule();
	private Id[] ids = new Id[Math.max(nOfLinks + 1, nOfBuses)];
	
	private TransitQueueSimulation sim = null;
	
	private void createIds() {
		for (int i = 0; i < ids.length; i++) {
			ids[i] = scenario.createId(Integer.toString(i));
		}
	}

	private void prepareConfig() {
		Config config = scenario.getConfig();
		config.simulation().setSnapshotStyle("queue");
		config.simulation().setEndTime(24.0*3600);
	}
	
	private void createNetwork() {
		NetworkLayer network = (NetworkLayer) scenario.getNetwork();
		network.setCapacityPeriod(3600.0);
		Node[] nodes = new Node[nOfLinks + 1];
		for (int i = 0; i <= nOfLinks; i++) {
			nodes[i] = network.createNode(ids[i], scenario.createCoord(i * 500, 0));
		}
		for (int i = 0; i < nOfLinks; i++) {
			network.createLink(ids[i], nodes[i], nodes[i+1], 500.0, 10.0, 1000.0, 1);
		}
	}
	
	private void createTransitSchedule() {
		TransitStopFacility[] stops = new TransitStopFacility[nOfLinks]; 
		ArrayList<TransitRouteStop> stopList = new ArrayList<TransitRouteStop>(nOfLinks);
		for (int i = 0; i < nOfLinks; i++) {
			stops[i] = new TransitStopFacility(ids[i], scenario.createCoord((i+1)*500, 0));
			stops[i].setLink(scenario.getNetwork().getLinks().get(ids[i]));
			this.schedule.addStopFacility(stops[i]);
			TransitRouteStop stop = new TransitRouteStop(stops[i], i * 50, i * 50 + 10);
			stopList.add(stop);
		}
		Link startLink = scenario.getNetwork().getLinks().get(ids[0]);
		Link endLink = scenario.getNetwork().getLinks().get(ids[nOfLinks - 1]);
		NetworkRoute networkRoute = (NetworkRoute) scenario.getNetwork().getFactory().createRoute(TransportMode.car, startLink, endLink);
		ArrayList<Link> linkList = new ArrayList<Link>(nOfLinks - 2);
		for (int i = 1; i < nOfLinks -1; i++) {
			linkList.add(scenario.getNetwork().getLinks().get(ids[i]));
		}
		networkRoute.setLinks(startLink, linkList, endLink);
		TransitRoute tRoute = new TransitRoute(ids[1], networkRoute, stopList, TransportMode.bus);

		TransitLine tLine = new TransitLine(ids[1]);
		tLine.addRoute(tRoute);
		this.schedule.addTransitLine(tLine);

		for (int i = 0; i < nOfBuses; i++	) {
			tRoute.addDeparture(new Departure(ids[i], departureTime + i*heading + (i == delayedBus ? delay : 0)));
		}
//		try {
//			new TransitScheduleWriterV1(this.schedule).write("accessEgressSchedule.xml");
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
	}
	
	private void createPopulation() {
		Population population = scenario.getPopulation();
		PopulationBuilder pb = population.getPopulationBuilder();
		TransitStopFacility[] stops = this.schedule.getFacilities().values().toArray(new TransitStopFacility[this.schedule.getFacilities().size()]);
		TransitLine tLine = this.schedule.getTransitLines().get(ids[1]);
		
		TransitStopFacility lastStop = this.schedule.getFacilities().get(ids[stops.length - 1]);
		for (int i = 0; i < stops.length; i++) {
			TransitStopFacility stop = stops[i];
			if (stop == lastStop) {
				continue;
			}
			for (int j = 0; j < nOfAgentsPerStop; j++) {
				Person person = pb.createPerson(scenario.createId(Integer.toString(i * nOfAgentsPerStop + j)));
				Plan plan = pb.createPlan(person);
				Activity act1 = pb.createActivityFromLinkId("home", ids[i]);
				act1.setEndTime(departureTime + j * agentInterval);
				Leg leg = pb.createLeg(TransportMode.pt);
				leg.setRoute(new ExperimentalTransitRoute(stop, tLine, lastStop));
				Activity act2 = pb.createActivityFromLinkId("work", ids[nOfLinks - 1]);
				
				population.getPersons().put(person.getId(), person);
				person.getPlans().add(plan);
				person.setSelectedPlan(plan);
				plan.addActivity(act1);
				plan.addLeg(leg);
				plan.addActivity(act2);
			}
		}
	}
	
	private void runSim() {
		Events events = new Events();
		
		VehicleTracker vehTracker = new VehicleTracker();
		events.addHandler(vehTracker);
		TransitRouteAccessEgressAnalysis analysis = new TransitRouteAccessEgressAnalysis(this.schedule.getTransitLines().get(ids[1]).getRoutes().get(ids[1]), vehTracker);
		events.addHandler(analysis);
		
		this.sim = new TransitQueueSimulation(scenario.getNetwork(), scenario.getPopulation(), events);
		this.sim.startOTFServer("access_egress_demo");
		this.sim.setTransitSchedule(this.schedule);
		
		OTFDemo.ptConnect("access_egress_demo");
		
		this.sim.run();
		
		analysis.printStats();
	}

	public void run() {
		createIds();
		prepareConfig();
		createNetwork();
		createTransitSchedule();
		createPopulation();
		runSim();
	}

	public static void main(String[] args) {
		new AccessEgressDemo().run();
	}

}
