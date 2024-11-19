/*******************************************************************************
 * Copyright (C) 2021 Andrei Olaru.
 * 
 * This file is part of Flash-MAS. The CONTRIBUTORS.md file lists people who have been previously involved with this project.
 * 
 * Flash-MAS is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or any later version.
 * 
 * Flash-MAS is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Flash-MAS.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package test.compositePingPong;

import net.xqhs.flash.FlashBoot;
import net.xqhs.flash.core.DeploymentConfiguration;
import net.xqhs.flash.core.DeploymentUtils;
import net.xqhs.flash.core.testVio.ArgsWrapper;
import net.xqhs.flash.core.testVio.EntityScheduler;
import net.xqhs.flash.core.testVio.SingleLine;
import net.xqhs.flash.core.util.MultiTreeMap;
import net.xqhs.util.logging.UnitComponentExt;

import java.util.*;
import java.util.concurrent.TimeUnit;


/**
 * Deployment testing.
 */
public class Boot {
	/**
	 * Designation for shards.
	 */
	public static final String FUNCTIONALITY = "TESTING";
	/**
	 * Different designation for shards.
	 */
	public static final String MONITORING = "OTHER-MONITORING";

	/**
	 * Performs test
	 *
	 * @param args_ - the command-line arguments.
	 */
	public static void main(String[] args_) {
		// Example command-line arguments
		String args = "";

		args += " -package testing -loader agent:composite";
		args += " -node node1";
		args += " -agent composite:AgentA -shard messaging -shard PingTest otherAgent:AgentB -shard EchoTesting";
		args += " -agent composite:AgentB -shard messaging -shard PingBackTest -shard EchoTesting";




		// Split the string into an array
		String[] cliArgs = args.split(" ");


		// Initialize the base context, root tree, and other necessary structures
		DeploymentUtils.PublicCtxtTriple baseContext = new DeploymentUtils.PublicCtxtTriple("root", null, null);



		MultiTreeMap rootTree = new MultiTreeMap();
		List<String> autoCreated = new ArrayList<>();
		Map<String, String> name_ids = new HashMap<>();
		UnitComponentExt log = new UnitComponentExt();  // Assuming this is your logging class

		DeploymentConfiguration config = new DeploymentConfiguration();
		EntityScheduler loaderScheduler =  new EntityScheduler();

		// Call the readCLIArgs method with the parsed arguments

		DeploymentUtils.publicReadCLIArgs( config, Arrays.asList(cliArgs).iterator(), baseContext, rootTree, autoCreated, name_ids, log);

		ArgsWrapper argsWrapper = new ArgsWrapper(args);
		loaderScheduler.scheduleEntity(()->{
			String agentBArgs = SingleLine.extractAgent( argsWrapper.getArgs(), "composite: AgentB");
			System.out.println("Extracted AgentB Args: " + agentBArgs);

		}, 2, TimeUnit.SECONDS);


		FlashBoot.main(cliArgs);
	}


}



