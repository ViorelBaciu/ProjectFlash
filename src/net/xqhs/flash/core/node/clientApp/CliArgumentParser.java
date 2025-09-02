package net.xqhs.flash.core.node.clientApp;

import java.util.LinkedList;
import java.util.List;

import net.xqhs.flash.core.CategoryName;
import net.xqhs.flash.core.DeploymentConfiguration;
import net.xqhs.flash.core.SimpleLoader;
import net.xqhs.flash.core.util.MultiTreeMap;

public class CliArgumentParser {

	public static final String CLASSPATH_KEY = "classpath";

	public static void addAgentFromCLI(String[] args, MultiTreeMap rootTree) {
		// Find the node and pylon to add the agent to.
		String pylonName = "local:default"; // Assuming a default pylon name from your config

		MultiTreeMap mainNodeTree = rootTree.getSingleTree(CategoryName.NODE.s(), true).getSingleTree("main", true);
		MultiTreeMap pylonContainer = mainNodeTree.getSingleTree(CategoryName.PYLON.s(), true);
		MultiTreeMap pylonTree = pylonContainer.getSingleTree(pylonName, true);

		// Manually build the MultiTreeMap for the new agent.
		MultiTreeMap newAgentConfig = new MultiTreeMap();
		List<MultiTreeMap> subordinateEntities = new LinkedList<>();

		String agentNameWithKind = null;
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-agent") && i + 1 < args.length) {
				agentNameWithKind = args[++i];
			} else if (args[i].equals("-shard") && i + 1 < args.length) {
				MultiTreeMap shardConfig = new MultiTreeMap();
				// Parse shard arguments
				String shardName = args[++i];
				shardConfig.addSingleValue(DeploymentConfiguration.NAME_ATTRIBUTE_NAME, shardName);
				if (i + 1 < args.length && args[i + 1].startsWith("classpath:")) {
					shardConfig.addSingleValue(SimpleLoader.CLASSPATH_KEY,
							args[i + 1].substring("classpath:".length()));
					i++;
				}
				subordinateEntities.add(shardConfig);
			}
		}

		if (agentNameWithKind == null) {
			System.err.println("Agent name is missing from CLI arguments.");
			return;
		}

		String[] nameParts = agentNameWithKind.split(":");
		String kind = nameParts[0];
		String name = nameParts[1];

		newAgentConfig.addSingleValue(DeploymentConfiguration.NAME_ATTRIBUTE_NAME, name);
		newAgentConfig.addSingleValue(DeploymentConfiguration.KIND_ATTRIBUTE_NAME, kind);
		newAgentConfig.addSingleValue(DeploymentConfiguration.CATEGORY_ATTRIBUTE_NAME, CategoryName.AGENT.s());
		newAgentConfig.addSingleValue(DeploymentConfiguration.LOCAL_ID_ATTRIBUTE, "#" + newAgentConfig.hashCode());

		// Add subordinate entities to the agent config
		if (!subordinateEntities.isEmpty()) {
			MultiTreeMap shardTree = newAgentConfig.addSingleTreeGet(CategoryName.SHARD.s(), new MultiTreeMap());
			for (MultiTreeMap shardConfig : subordinateEntities) {
				shardTree.addAll(shardConfig.getFirstValue(DeploymentConfiguration.NAME_ATTRIBUTE_NAME),
						(List<String>) shardConfig);
			}
		}

		// Add agent configuration to the main tree
		MultiTreeMap agentContainer = pylonTree.getSingleTree(CategoryName.AGENT.s(), true);
		agentContainer.addAll(name, (List<String>) newAgentConfig);
	}
}
