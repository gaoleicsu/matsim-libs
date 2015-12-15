/*
 *  *********************************************************************** *
 *  * project: org.matsim.*
 *  * CountSimComparisonModule.java
 *  *                                                                         *
 *  * *********************************************************************** *
 *  *                                                                         *
 *  * copyright       : (C) 2014 by the members listed in the COPYING, *
 *  *                   LICENSE and WARRANTY file.                            *
 *  * email           : info at matsim dot org                                *
 *  *                                                                         *
 *  * *********************************************************************** *
 *  *                                                                         *
 *  *   This program is free software; you can redistribute it and/or modify  *
 *  *   it under the terms of the GNU General Public License as published by  *
 *  *   the Free Software Foundation; either version 2 of the License, or     *
 *  *   (at your option) any later version.                                   *
 *  *   See also COPYING, LICENSE and WARRANTY file                           *
 *  *                                                                         *
 *  * ***********************************************************************
 */

package org.matsim.counts;

import com.google.inject.Provides;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.groups.CountsConfigGroup;
import org.matsim.core.controler.AbstractModule;

import javax.inject.Inject;
import javax.inject.Singleton;

public class CountsModule extends AbstractModule {

    @Override
    public void install() {
        addControlerListenerBinding().to(CountsControlerListener.class);
        bind(CountsInitializer.class).asEagerSingleton();
    }

    private static class CountsInitializer {
        @Inject
        CountsInitializer(Counts<Link> counts, Scenario scenario) {
            Counts<Link> scenarioCounts = (Counts<Link>) scenario.getScenarioElement(Counts.ELEMENT_NAME);
            if (scenarioCounts == null) {
                scenario.addScenarioElement(Counts.ELEMENT_NAME, counts);
            } else {
                if (counts != scenarioCounts) {
                    throw new RuntimeException();
                }
            }
        }
    }

    @Provides
    @Singleton
    Counts<Link> provideLinkCounts(Scenario scenario, CountsConfigGroup config) {
        Counts<Link> counts = (Counts<Link>) scenario.getScenarioElement(Counts.ELEMENT_NAME);
        if (counts != null) {
            return counts;
        } else {
            counts = new Counts<>();
            if (config.getCountsFileName() != null) {
                MatsimCountsReader counts_parser = new MatsimCountsReader(counts);
                counts_parser.readFile(config.getCountsFileName());
            }
            return counts;
        }
    }

}
